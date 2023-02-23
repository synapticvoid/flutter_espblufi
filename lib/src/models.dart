import 'package:flutter/foundation.dart';

@immutable
class BLEDevice {
  final String macAddress;
  final String name;
  final int rssi;

  const BLEDevice(this.macAddress, this.name, this.rssi);

  factory BLEDevice.fromMap(Map<String, dynamic> data) => BLEDevice(
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

class BlufiStatusCode {
  static const statusSuccess = 0;
  static const codeInvalidNotification = -1000;
  static const codeCatchException = -1001;
  static const codeWriteDataFailed = -1002;
  static const codeInvalidData = -1003;

  static const codeNegPostFailed = -2000;
  static const codeNegErrDevKey = -2001;
  static const codeNegErrSecurity = -2002;
  static const codeNegErrSetSecurity = -2003;

  static const codeConfInvalidOpmode = -3000;
  static const codeConfErrSetOpmode = -3001;
  static const codeConfErrPostSta = -3002;
  static const codeConfErrPostSoftap = -3003;

  static const codeGattWriteTimeout = -4000;

  static const codeWifiScanFail = 11;
}
