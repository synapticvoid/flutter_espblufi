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
  final _espblufiPlugin = Espblufi();

  String deviceMessage = "";
  final List<BlufiEvent> blufiEvents = [];

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: [
            Row(
              children: [
                ElevatedButton(
                    onPressed: () {
                      _espblufiPlugin.startScan(filter: "BLUFI");
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
            StreamBuilder<BlufiEvent>(
              stream: _espblufiPlugin.events(),
              builder: (context, snapshot) {
                if (snapshot.hasError) {
                  print("snapshot error=${snapshot.error}");
                }

                if (snapshot.data != null) {
                  print("Got an event ! ${snapshot.data}");
                  blufiEvents.add(snapshot.data!);
                }

                return ListView.builder(
                    shrinkWrap: true,
                    itemCount: blufiEvents.length,
                    itemBuilder: (context, index) {
                      final event = blufiEvents[index];
                      String text = "type=${event.type}. ";

                      switch (event.runtimeType) {
                        case BlufiEventConnectionState:
                          final e = event as BlufiEventConnectionState;
                          text += "connected=${e.connected}";
                          break;
                        case BlufiEventCustomDataReceived:
                          final e = event as BlufiEventCustomDataReceived;
                          text += "data=${e.data}";
                          break;
                      }
                      return Text(text);
                    });
              },
            ),
            Expanded(
              child: StreamBuilder<List<BLEDevice>>(
                  stream: _espblufiPlugin.scanResults(),
                  builder: (context, snapshot) {
                    // print("snapshot=$snapshot, snapshot.data=${snapshot.data}");
                    final List<BLEDevice> list = snapshot.data ?? [];
                    // if (snapshot.data != null) {
                    //   list.add(snapshot.data!);
                    // }

                    return ListView.builder(
                        itemCount: list.length,
                        itemBuilder: (context, index) {
                          final device = list[index];
                          return Column(
                            children: [
                              Text(device.name),
                              Text("${device.rssi} RSSI"),
                              Row(
                                children: [
                                  ElevatedButton(
                                      onPressed: () {
                                        _espblufiPlugin.connect(device.macAddress);
                                      },
                                      child: Text("Connect")),
                                  ElevatedButton(
                                      onPressed: () async {
                                        String version =
                                            await _espblufiPlugin.requestDeviceVersion();
                                        setState(() {
                                          deviceMessage = "Connected to $device, version=$version";
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
            ),
          ],
        ),
      ),
    );
  }
}
