import 'package:flutter/foundation.dart';

import 'espblufi_platform_interface.dart';

class BLEDevice {
  final String macAddress;
  final String name;
  final int rssi;

  BLEDevice(this.macAddress, this.name, this.rssi);

  factory BLEDevice.fromMap(Map<String, dynamic> data) => BLEDevice(
        data["macAddress"],
        data["name"],
        data["rssi"],
      );
}

@immutable
class BlufiEvent {
  static const typeConnectionState = 1;
  static const typeCustomData = 2;

  final int type;

  BlufiEvent(this.type);
}

class BlufiEventConnectionState extends BlufiEvent {
  final bool connected;

  BlufiEventConnectionState(this.connected) : super(BlufiEvent.typeConnectionState);
}

class BlufiEventCustomDataReceived extends BlufiEvent {
  final String data;

  BlufiEventCustomDataReceived(this.data) : super(BlufiEvent.typeCustomData);
}

BlufiEvent mapToBlufiEvent(Map<String, dynamic> map) {
  switch (map["type"]) {
    case BlufiEvent.typeConnectionState:
      return BlufiEventConnectionState(map["connected"]);
    case BlufiEvent.typeCustomData:
      return BlufiEventCustomDataReceived(map["data"]);
    default:
      throw Exception("Unhandled Blufi event type=${map['type']}");
  }
}

class Espblufi {
  Stream<List<BLEDevice>> scanResults() => EspblufiPlatform.instance.scanResults;

  Stream<BlufiEvent> events() => EspblufiPlatform.instance.events;

  // FIXME Remove this test code
  Future<String?> getPlatformVersion() => EspblufiPlatform.instance.getPlatformVersion();

  Future<void> startScan() => EspblufiPlatform.instance.startScan();

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(String data) => EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();
}
