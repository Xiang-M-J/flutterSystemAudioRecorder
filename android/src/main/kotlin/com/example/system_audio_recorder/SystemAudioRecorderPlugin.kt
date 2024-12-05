package com.example.system_audio_recorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.foregroundservice.ForegroundService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


/** SystemAudioRecorderPlugin */
class SystemAudioRecorderPlugin: MethodCallHandler, PluginRegistry.ActivityResultListener, FlutterPlugin,
  ActivityAware {

  private lateinit var channel : MethodChannel
  private var mProjectionManager: MediaProjectionManager? = null
  private var mMediaProjection: MediaProjection? = null
  private val RECORD_REQUEST_CODE = 333
  var TAG: String = "system_audio_recorder"
  private var eventSink: EventSink? = null

  private lateinit var _result: Result

  private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
  private var activityBinding: ActivityPluginBinding? = null;
  private var recordingThread: Thread? = null

  private var mAudioRecord: AudioRecord? = null
  private var isRecording: Boolean = false
  private var rSampleRate: Int = 16000
  private var rBufferSize = 640

  private val rAudioChannel: Int = AudioFormat.CHANNEL_IN_MONO
  private val rAudioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
  private val bytesPerElement = 2
//  private var root: File? = null
  private var cache: File? = null
  private var rawOutput: File? = null
  private var isRequired = false
  private var rToStream = true
  private var rToFile = false
  private var rFilePath: String? = null
  private var outputStream: FileOutputStream? = null
  private var outputPath: String? = null

  private var binaryMessenger: BinaryMessenger? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {

    pluginBinding = flutterPluginBinding
    binaryMessenger = flutterPluginBinding.binaryMessenger;

  }

  @RequiresApi(Build.VERSION_CODES.Q)
  // 在 ForegroundService 的startCommand执行完后执行
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == RECORD_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        mMediaProjection = mProjectionManager?.getMediaProjection(resultCode, data!!)
        _result.success(true)
        isRequired = true
        return true
      } else {
        isRequired = false
        _result.success(false)
      }
    }
    return false
  }

  private fun resetParameters(){
    rSampleRate = 16000
    rBufferSize = 640
    rFilePath = null
    rToStream = true
    rToFile = false
    isRequired = false
    outputPath = null
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun onMethodCall(call: MethodCall, result: Result) {
    val appContext = pluginBinding!!.applicationContext

    if (call.method == "getPlatformVersion") {
      result.success("Android ${Build.VERSION.RELEASE}")
    } else if (call.method == "requestRecord"){
      try {
        _result = result

        ForegroundService.startService(appContext, "开始录音", "开始录音")
        mProjectionManager =
          appContext.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

        val permissionIntent = mProjectionManager?.createScreenCaptureIntent()
        Log.i(TAG, "startActivityForResult")

        // 调用 ForegroundService的 startCommand 方法
        ActivityCompat.startActivityForResult(
          activityBinding!!.activity,
          permissionIntent!!,
          RECORD_REQUEST_CODE,
          null
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error onMethodCall startRecord: ${e.message}")
        result.success(false)
      }
    }
    else if (call.method == "startRecord"){
      try {
        val sampleRate = call.argument<Int>("sampleRate")
        if (sampleRate != null){
          rSampleRate = sampleRate
        }
        val bufferSize = call.argument<Int?>("bufferSize")
        if (bufferSize != null){
          rBufferSize = bufferSize
        }
        val toStream = call.argument<Boolean?>("toStream")
        if (toStream != null){
          rToStream = toStream
        }
        val toFile = call.argument<Boolean?>("toFile")
        if (toFile != null){
          rToFile = toFile
        }
        val filePath = call.argument<String?>("filePath")
        if (filePath != null){
          rFilePath = filePath
        }
        if (rToStream){
          EventChannel(binaryMessenger, "system_audio_recorder/audio_stream").setStreamHandler(
            object : StreamHandler {
              override fun onListen(args: Any, events: EventSink?) {
                Log.i(TAG, "Adding listener")
                eventSink = events
              }

              override fun onCancel(args: Any) {
                eventSink = null
              }
            }
          )
        }

        startRecording(mMediaProjection!!)
        result.success(true)
      }catch (e: Exception){
        result.success(false)
      }


    }
    else if (call.method == "stopRecord"){
      Log.i(TAG, "stopRecord")
      try {
        ForegroundService.stopService(appContext)
        if (mAudioRecord != null){
          stop()
          if (outputPath != null){
            result.success(outputPath)
          }else{
            result.success("")
          }
        }else{
          result.success("")
        }
      } catch (e: Exception) {
        result.success("")
      }
    }
    else {
      result.notImplemented()
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  fun startRecording(mProjection: MediaProjection): Boolean {
    Log.i(TAG, "startRecording")
    if (mAudioRecord == null){
      val config : AudioPlaybackCaptureConfiguration
      try {
        config = AudioPlaybackCaptureConfiguration.Builder(mProjection)
          .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
          .addMatchingUsage(AudioAttributes.USAGE_GAME)
          .build()
      } catch (e: NoClassDefFoundError) {
        return false
      }
      val format = AudioFormat.Builder()
        .setEncoding(rAudioEncoding)
        .setSampleRate(rSampleRate)
        .setChannelMask(rAudioChannel)
        .build()

      mAudioRecord = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(rBufferSize).setAudioPlaybackCaptureConfig(config).build()
      isRecording = true
      mAudioRecord!!.startRecording()

      if (rToFile){
        createAudioFile()
      }

      recordingThread = Thread({ recording() }, "System Audio Capture")
      recordingThread!!.start()

    }
    return true
  }

  @Throws(IOException::class)
  private fun rawToWave(rawFile: File, waveFile: File) {
    val rawData = ByteArray(rawFile.length().toInt())
    var input: DataInputStream? = null
    try {
      input = DataInputStream(FileInputStream(rawFile))
      input.read(rawData)
    } finally {
      input?.close()
    }

    var output: DataOutputStream? = null
    try {
      output = DataOutputStream(FileOutputStream(waveFile))

      // WAVE header
      writeString(output, "RIFF") // chunk id
      writeInt(output, 36 + rawData.size) // chunk size
      writeString(output, "WAVE") // format
      writeString(output, "fmt ") // subchunk 1 id
      writeInt(output, 16) // subchunk 1 size
      writeShort(output, 1.toShort()) // audio format (1 = PCM)
      writeShort(output, 1.toShort()) // number of channels
      writeInt(output, rSampleRate) // sample rate
      writeInt(output, rSampleRate) // byte rate
      writeShort(output, 2.toShort()) // block align
      writeShort(output, 16.toShort()) // bits per sample
      writeString(output, "data") // subchunk 2 id
      writeInt(output, rawData.size) // subchunk 2 size
      // Audio data (conversion big endian -> little endian)
      val shorts = ShortArray(rawData.size / 2)
      ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
      val bytes = ByteBuffer.allocate(shorts.size * 2)
      for (s in shorts) {
        bytes.putShort(s)
      }

      output.write(fullyReadFileToBytes(rawFile))
    } finally {
      output?.close()
    }
  }

  @Throws(IOException::class)
  fun fullyReadFileToBytes(f: File): ByteArray {
    val size = f.length().toInt()
    val bytes = ByteArray(size)
    val tmpBuff = ByteArray(size)
    val fis = FileInputStream(f)
    try {
      var read = fis.read(bytes, 0, size)
      if (read < size) {
        var remain = size - read
        while (remain > 0) {
          read = fis.read(tmpBuff, 0, remain)
          System.arraycopy(tmpBuff, 0, bytes, size - remain, read)
          remain -= read
        }
      }
    } catch (e: IOException) {
      throw e
    } finally {
      fis.close()
    }

    return bytes
  }

  private fun createAudioFile() {
//    root = File(Environment.getExternalStorageDirectory(), "/System Audio record")
//    File(rFileName)
    cache = File(pluginBinding!!.applicationContext.cacheDir.absolutePath, "/RawData")

//    if (!root!!.exists()) {
//      root!!.mkdir()
//      root!!.setWritable(true)
//    }
    if (!cache!!.exists()) {
      cache!!.mkdir()
      cache!!.setWritable(true)
      cache!!.setReadable(true)
    }

    rawOutput = File(cache, "raw.pcm")

    try {
      rawOutput!!.createNewFile()
    } catch (e: IOException) {
      Log.e(TAG, "createAudioFile: $e")
      e.printStackTrace()
    }

    Log.d(TAG, "path: " + rawOutput!!.absolutePath)

  }

  @Throws(IOException::class)
  private fun writeInt(output: DataOutputStream, value: Int) {
    output.write(value shr 0)
    output.write(value shr 8)
    output.write(value shr 16)
    output.write(value shr 24)
  }

  @Throws(IOException::class)
  private fun writeShort(output: DataOutputStream, value: Short) {
    output.write(value.toInt() shr 0)
    output.write(value.toInt() shr 8)
  }

  @Throws(IOException::class)
  private fun writeString(output: DataOutputStream, value: String) {
    for (element in value) {
      output.write(element.code)
    }
  }

  private fun shortToByte(data: ShortArray): ByteArray {
    val arraySize = data.size
    val bytes = ByteArray(arraySize * 2)
    for (i in 0 until arraySize) {
      bytes[i * 2] = (data[i].toInt() and 0x00FF).toByte()
      bytes[i * 2 + 1] = (data[i].toInt() shr 8).toByte()
      data[i] = 0
    }
    return bytes
  }

  private fun recording() {
    try {
      if (rToFile){
        outputStream = FileOutputStream(rawOutput!!.absolutePath)
      }

      val data = ShortArray(rBufferSize)

      while (isRecording) {
        mAudioRecord!!.read(data, 0, rBufferSize)

//        val buffer = ByteBuffer.allocate(8 * 1024)
        val byte = shortToByte(data)
        if (rToStream){
          activityBinding!!.activity.runOnUiThread{
            eventSink!!.success(byte)
          }
        }

        if (rToFile){
          outputStream?.write(
            byte,
            0,
            rBufferSize * bytesPerElement
          )
        }
      }
      if (rToFile){
        outputStream?.close()
      }
    } catch (e: FileNotFoundException) {
      Log.e(TAG, "File Not Found: $e")
      e.printStackTrace()
    } catch (e: IOException) {
      Log.e(TAG, "IO Exception: $e")
      e.printStackTrace()
    }
  }

  private fun startProcessing() {

    //Convert To mp3 from raw data i.e pcm
    if (rFilePath == null){
      return
    }
    val output = File(rFilePath!!)
    outputPath = output.absolutePath
    try {
      output.createNewFile()
    } catch (e: IOException) {
      e.printStackTrace()
      Log.e(TAG, "startProcessing: $e")
    }

    try {
      rawOutput?.let { rawToWave(it, output) }
    } catch (e: IOException) {
      e.printStackTrace()
    } finally {
      rawOutput!!.delete()
    }
  }

  private fun stop(){
    isRecording = false
    mAudioRecord!!.stop()
    mAudioRecord!!.release()
    if (rToFile){
      startProcessing()
    }
    if (mAudioRecord != null){
      mAudioRecord = null
      recordingThread = null
    }
    resetParameters()
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityBinding = binding;
    channel = MethodChannel(pluginBinding!!.binaryMessenger, "system_audio_recorder")
    channel.setMethodCallHandler(this)
    activityBinding!!.addActivityResultListener(this);
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activityBinding = binding;
  }

  override fun onDetachedFromActivity() {}
}
