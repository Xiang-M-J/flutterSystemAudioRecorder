# system_audio_recorder

A Simple flutter plugin used to record audio played by the Android system





## Functions

### requestRecord

This function is used to request system permission to record played audio. When this function is called, the system will display a prompt. Clicking 'Allow' returns `true`, while clicking 'Deny' returns `false`.(Although this function can accept parameters, it is unnecessary to pass any)



### startRecord

Call this function to start recording the audio played by the system. You must first call `requestRecord` to request permission before calling this function. **Currently, only PCM16 encoding is supported.**



| Argument   | Type    | Description                                       | Default |
| ---------- | ------- | ------------------------------------------------- | ------- |
| sampleRate | int     |                                                   | 16000   |
| bufferSize | int     | Buffer size in bytes                              | 640     |
| toStream   | bool    | Whether to receive the recorded audio as a stream | true    |
| toFile     | bool    | Whether to save the recorded audio to a file      | false   |
| filePath   | String? | File path where the recorded audio is saved       | null    |



### stopRecord

This function is used to stop the recording. If `toFile` is `true`, it returns the file path; otherwise, it returns `""`



## Example

### Permission configuration

In `android/app/src/main/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        ...
        >
        
        <!-- Add Service begin -->
        <service
            android:name="com.foregroundservice.ForegroundService"
            android:foregroundServiceType="mediaProjection">
        </service>
        <!-- Add Service end -->

		...
    </application>

    ...
    
    <!-- Add Permission begin -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_INTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <!-- Add Permission end -->
    
</manifest>

```





### Start record

```dart
bool isConfirmed = await SystemAudioRecorder.requestRecord(
    titleNotification: "titleNotification",
    messageNotification: "messageNotification",
);
bool toStream = true;
bool toFile = true;
String? path;    // path can not be null when toFile is true
if (isConfirmed) {
    audioData.clear();
    if (toFile){ // if record to file, get filepath
        var tempDir = await getExternalStorageDirectory();

        path = '${tempDir?.parent.path}/record.mp3';
        var outputFile = File(path);
        if (outputFile.existsSync()) {
            await outputFile.delete();
        }
    }
    bool isStart = await SystemAudioRecorder.startRecord(toStream: toStream, toFile: toFile, filePath: path);
    if (toStream){  // if record to stream, set stream listener
        _audioSubscription ??=
            SystemAudioRecorder.audioStream.receiveBroadcastStream({}).listen((data) {
            audioData.addAll(List<int>.from(data));   // data.length = bufferSize * 2
        });
    }

}
```



### Stop record

```dart
String path = await SystemAudioRecorder.stopRecord();
if (_audioSubscription != null) {   // if record to stream, cancel subscription when stop
    _audioSubscription?.cancel();
    _audioSubscription = null;
}
```

