import 'dart:async';

import 'package:espblufi/espblufi.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
  final _espblufiPlugin = Espblufi();

  String deviceMessage = "";

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion = await _espblufiPlugin.getPlatformVersion() ?? 'Unknown platform version';
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
        body: Column(
          children: [
            Text('Running on: $_platformVersion\n'),
            Row(
              children: [
                ElevatedButton(
                    onPressed: () {
                      _espblufiPlugin.startScan();
                    },
                    child: Text("Scan")),
                ElevatedButton(
                    onPressed: () {
                      _espblufiPlugin.disconnect();
                    },
                    child: Text("Disconnect")),
              ],
            ),
            Text(deviceMessage),
            StreamBuilder<String>(
              stream: _espblufiPlugin.state(),
              builder: (context, snapshot) {
                final state = snapshot.data ?? "";
                return Text("state=$state");
              },
            ),
            Expanded(
              child: StreamBuilder<String>(
                  stream: _espblufiPlugin.scanResults(),
                  builder: (context, snapshot) {
                    final list = [snapshot.data ?? ""];

                    return ListView.builder(
                        itemCount: list.length,
                        itemBuilder: (context, index) {
                          final item = list[index];
                          return Column(
                            children: [
                              Text(item),
                              Row(
                                children: [
                                  ElevatedButton(
                                      onPressed: () {
                                        _espblufiPlugin.connect(item);
                                      },
                                      child: Text("Connect")),
                                  ElevatedButton(
                                      onPressed: () async {
                                        String version =
                                            await _espblufiPlugin.requestDeviceVersion();
                                        setState(() {
                                          deviceMessage = "Connected to $item, version=$version";
                                        });
                                      },
                                      child: Text("Version")),
                                  ElevatedButton(
                                      onPressed: () async {
                                        await _espblufiPlugin.postCustomData("v");
                                      },
                                      child: Text("Custom v")),
                                ],
                              ),
                            ],
                          );
                        });
                  }),
            )
          ],
        ),
      ),
    );
  }
}
