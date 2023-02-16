import 'package:flutter/foundation.dart';

@immutable
class BLEDevice {
  final String macAddress;
  final String name;
  final int rssi;

  const BLEDevice(this.macAddress, this.name, this.rssi);

  factory BLEDevice.fromMap(Map<String, dynamic> data) =>
      BLEDevice(
        data["macAddress"],
        data["name"],
        data["rssi"],
      );
}

@immutable
class BLEScanEvent {
  static const typeIdle = 1;
  static const typeInProgress = 2;
  final int type;

  const BLEScanEvent(this.type);
}

class BLEScanEventIdle extends BLEScanEvent {
  const BLEScanEventIdle() : super(BLEScanEvent.typeIdle);
}

class BLEScanEventInProgress extends BLEScanEvent {
  final List<BLEDevice> devices;

  const BLEScanEventInProgress(this.devices) : super(BLEScanEvent.typeInProgress);
}

@immutable
class DeviceStatus {
  final String staSSID;
  final String staPassword;

  const DeviceStatus(this.staSSID, this.staPassword);

  @override
  String toString() {
    return 'DeviceStatus{staSSID: $staSSID, staPassword: $staPassword}';
  }
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
  final Uint8List data;

  const BlufiEventCustomDataReceived(this.data) : super(BlufiEvent.typeCustomData);
}
