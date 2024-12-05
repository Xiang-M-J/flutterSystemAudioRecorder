import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'system_audio_recorder_platform_interface.dart';

/// An implementation of [SystemAudioRecorderPlatform] that uses method channels.
class MethodChannelSystemAudioRecorder extends SystemAudioRecorderPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('system_audio_recorder');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<bool> requestRecord(
  {
    String notificationTitle = "",
    String notificationMessage = "",
}
      )async{
    final bool isOk = await methodChannel.invokeMethod('requestRecord', {

    });
    return isOk;
  }

  @override
  Future<bool> startRecord(
      String name, {
        int? sampleRate,
        int? bufferSize, bool? toStream, bool? toFile, String? filePath
      }) async {
    final bool start = await methodChannel.invokeMethod('startRecord', {
      "name": name,
      "sampleRate": sampleRate,
      "bufferSize": bufferSize,
      "toStream": toStream,
      "toFile": toFile,
      "filePath": filePath
    });
    return start;
  }


  @override
  Future<String> stopRecord() async {
    final String path = await methodChannel.invokeMethod('stopRecord');
    return path;
  }
}
