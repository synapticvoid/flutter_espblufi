import 'package:espblufi/src/models.dart';

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

DeviceStatus mapToDeviceStatus(Map<String, dynamic> map) => DeviceStatus(
      map["staSSID"] ?? "",
      map["staPassword"] ?? "",
    );

List<WifiScanResult> mapToWifiScanResult(Iterable<Map<String, dynamic>> list) =>
    list.map((e) => WifiScanResult(e["ssid"], e["rssi"])).toList();

BLEScanEvent mapToBLEScanEvent(Map<String, dynamic> map) {
  switch (map["type"]) {
    case BLEScanEvent.typeIdle:
      return const BLEScanEventIdle();
    case BLEScanEvent.typeInProgress:
      final devices = List<dynamic>.from(map["devices"])
          .map((e) => BLEDevice.fromMap(Map<String, dynamic>.from(e)))
          .toList();
      return BLEScanEventInProgress(devices);
    default:
      throw Exception("Unhandled Blufi event type=${map['type']}");
  }
}
