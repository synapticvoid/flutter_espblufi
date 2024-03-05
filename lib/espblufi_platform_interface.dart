import 'dart:typed_data';

import 'package:espblufi/espblufi.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'espblufi_method_channel.dart';

abstract class EspblufiPlatform extends PlatformInterface {
  /// Constructs a EspblufiPlatform.
  EspblufiPlatform() : super(token: _token);

  static final Object _token = Object();

  static EspblufiPlatform _instance = MethodChannelEspblufi();

  /// The default instance of [EspblufiPlatform] to use.
  ///
  /// Defaults to [MethodChannelEspblufi].
  static EspblufiPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [EspblufiPlatform] when
  /// they register themselves.
  static set instance(EspblufiPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> startScan(
      {String? filter, Duration timeout = defaultScanDuration});

  Stream<BLEScanEvent> get scanResults;

  Stream<BlufiEvent> get events;

  Future<bool> isBluetoothEnabled();

  Future<void> connect(String macAddress);

  Future<void> disconnect();

  Future<String> requestDeviceVersion();

  Future<void> postCustomData(Uint8List data);

  Future<DeviceStatus> requestDeviceStatus();

  Future<List<WifiScanResult>> requestWifiScan();

  Future<int> configureParameters(BlufiConfigureParams params);
}
