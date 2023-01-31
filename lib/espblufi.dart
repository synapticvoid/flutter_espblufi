import 'package:flutter/foundation.dart';

import 'espblufi_platform_interface.dart';

const Duration defaultScanDuration = Duration(seconds: 5);

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

  const BlufiEvent(this.type);
}

class BlufiEventConnectionState extends BlufiEvent {
  final bool connected;

  const BlufiEventConnectionState(this.connected) : super(BlufiEvent.typeConnectionState);
}

class BlufiEventCustomDataReceived extends BlufiEvent {
  final String data;

  const BlufiEventCustomDataReceived(this.data) : super(BlufiEvent.typeCustomData);
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

  Future<void> startScan({String? filter, Duration timeout = defaultScanDuration}) =>
      EspblufiPlatform.instance.startScan(filter: filter, timeout: timeout);

  Future<void> connect(String macAddress) => EspblufiPlatform.instance.connect(macAddress);

  Future<void> postCustomData(String data) => EspblufiPlatform.instance.postCustomData(data);

  Future<void> disconnect() => EspblufiPlatform.instance.disconnect();

  Future<String> requestDeviceVersion() => EspblufiPlatform.instance.requestDeviceVersion();
}
