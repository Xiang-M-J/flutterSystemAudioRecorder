import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:system_audio_recorder/system_audio_recorder.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_sound/flutter_sound.dart';
import 'package:path_provider/path_provider.dart';


void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _systemAudioRecorderPlugin = SystemAudioRecorder();
  StreamSubscription? _audioSubscription;
  List<int> audioData = List.empty(growable: true);
  Uint8List? udata;
  FlutterSoundPlayer player = FlutterSoundPlayer();
  requestPermissions() async {
    if (!kIsWeb) {
      if (await Permission.storage.request().isDenied) {
        await Permission.storage.request();
      }
    }
  }

  @override
  void initState() {
    super.initState();
    requestPermissions();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion = await _systemAudioRecorderPlugin.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(children: [
          Text('Running on: $_platformVersion\n'),
          TextButton(
              onPressed: () async {
                bool isConfirmed = await SystemAudioRecorder.requestRecord(
                  titleNotification: "titleNotification",
                  messageNotification: "messageNotification",
                );
                bool toStream = true;
                bool toFile = true;
                String? path;    // path can not be null when toFile is true
                if (isConfirmed) {
                  audioData.clear();
                  if (toFile){
                    var tempDir = await getExternalStorageDirectory();

                    path = '${tempDir?.parent.path}/record.mp3';
                    var outputFile = File(path);
                    if (outputFile.existsSync()) {
                      await outputFile.delete();
                    }
                  }
                  bool isStart = await SystemAudioRecorder.startRecord(toStream: toStream, toFile: toFile, filePath: path);
                  if (toStream){
                    _audioSubscription ??=
                        SystemAudioRecorder.audioStream.receiveBroadcastStream({}).listen((data) {
                          audioData.addAll(List<int>.from(data));
                        });
                  }

                }
              },
              child: const Text("开始录制")),
          TextButton(
              onPressed: () async {
                String path = await SystemAudioRecorder.stopRecord();
                if (_audioSubscription != null) {
                  _audioSubscription?.cancel();
                  _audioSubscription = null;
                }
                if(path != "") print(path);
              },
              child: const Text("停止录制")),
          TextButton(onPressed: () async {
            udata = Uint8List.fromList(audioData);
            await player.openPlayer();
            await player.startPlayerFromStream(codec: Codec.pcm16, numChannels: 1, sampleRate: 16000);
            await player.feedFromStream(udata!);
            await player.stopPlayer();

          }, child: const Text("开始播放"))
        ]),
      ),
    );
  }
}
