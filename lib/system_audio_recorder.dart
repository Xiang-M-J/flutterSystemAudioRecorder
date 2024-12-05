
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'system_audio_recorder_platform_interface.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

class SystemAudioRecorder {
  static const EventChannel audioStream = EventChannel('system_audio_recorder/audio_stream');
  Future<String?> getPlatformVersion() {
    return SystemAudioRecorderPlatform.instance.getPlatformVersion();
  }
  static Future<bool> requestRecord({String? titleNotification, String? messageNotification}) async{
    try {

      titleNotification ??= "";
      messageNotification ??= "";
      await _maybeStartFGS(titleNotification, messageNotification);
      final bool isOk = await SystemAudioRecorderPlatform.instance.requestRecord();

      return isOk;
    } catch (err) {
      print("requestRecord error");
      print(err);
    }

    return false;
  }
  static Future<bool> startRecord({int? sampleRate = 16000, int? bufferSize = 640, bool? toStream = true, bool? toFile = false, String? filePath }) async {
    try {
      sampleRate ??= 16000;
      bufferSize ??= 640;
      toStream ??= true;
      toFile ??= false;
      if (toFile && filePath == null){
        throw Exception("filePath can not be null when to File is true");
      }

      final bool start = await SystemAudioRecorderPlatform.instance.startRecord(
        "record",
        sampleRate: sampleRate,
        bufferSize: bufferSize,
        toStream: toStream,
        toFile: toFile,
        filePath: filePath
      );

      return start;
    } catch (err) {
      print("startRecord err");
      print(err);
    }
    return false;
  }

  static Future<String> stopRecord() async {
    try {
      final String path = await SystemAudioRecorderPlatform.instance.stopRecord();
      if (!kIsWeb && Platform.isAndroid) {
        FlutterForegroundTask.stopService();
      }
      return path;
    } catch (err) {
      print("stopRecord err");
      print(err);
    }
    return "";
  }

  // static Stream<List<int>> get audioStream {
  //   return _audioStream.receiveBroadcastStream().map((data) => List<int>.from(data));
  // }

  static _maybeStartFGS(String titleNotification, String messageNotification) {
    try {
      if (!kIsWeb && Platform.isAndroid) {
        FlutterForegroundTask.init(
          androidNotificationOptions: AndroidNotificationOptions(
            channelId: 'notification_channel_id',
            channelName: titleNotification,
            channelDescription: messageNotification,
            channelImportance: NotificationChannelImportance.LOW,
            priority: NotificationPriority.LOW,
            iconData: const NotificationIconData(
              resType: ResourceType.mipmap,
              resPrefix: ResourcePrefix.ic,
              name: 'launcher',
            ),
          ),
          iosNotificationOptions: const IOSNotificationOptions(
            showNotification: true,
            playSound: false,
          ),
          foregroundTaskOptions: const ForegroundTaskOptions(
            interval: 5000,
            autoRunOnBoot: true,
            allowWifiLock: true,
          ),
        );
      }
    } catch (err) {
      print("_maybeStartFGS err");
      print(err);
    }
  }

}
