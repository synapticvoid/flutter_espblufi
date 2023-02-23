import 'dart:convert';
import 'dart:typed_data';

import 'package:espblufi/espblufi.dart';
import 'package:flutter/material.dart';

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

  final List<BLEDevice> devices = [];

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
                      devices.clear();
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
              child: StreamBuilder<BLEScanEvent>(
                  stream: _espblufiPlugin.scanResults(),
                  builder: (context, snapshot) {
                    // print("snapshot=$snapshot, snapshot.data=${snapshot.data}");
                    final event = snapshot.data;

                    if (event is BLEScanEventInProgress) {
                      for (final device in event.devices) {
                        devices.removeWhere((element) => element.macAddress == device.macAddress);
                      }
                      devices.addAll(event.devices);
                    }

                    // if (snapshot.data != null) {
                    //   list.add(snapshot.data!);
                    // }

                    return ListView.builder(
                        itemCount: devices.length,
                        itemBuilder: (context, index) {
                          final device = devices[index];
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
                                ],
                              ),
                              Row(
                                children: [
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
                                        DeviceStatus status =
                                            await _espblufiPlugin.requestDeviceStatus();
                                        setState(() {
                                          deviceMessage = "Device status=$status";
                                        });
                                      },
                                      child: Text("Status")),
                                  ElevatedButton(
                                      onPressed: () async {
                                        final data = "Custom";
                                        final encoded = Uint8List.fromList(utf8.encode(data));
                                        print("data=$data. encoded=$encoded");
                                        await _espblufiPlugin.postCustomData(encoded);
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
