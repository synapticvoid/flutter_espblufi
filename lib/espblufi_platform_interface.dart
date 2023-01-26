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

  Future<String?> getPlatformVersion();

  Future<void> startScan();

  Stream<String> get scanResults;

  Future<void> connect(String macAddress);

  Future<void> disconnect();
}
