import 'package:flutter_test/flutter_test.dart';
import 'package:espblufi/espblufi.dart';
import 'package:espblufi/espblufi_platform_interface.dart';
import 'package:espblufi/espblufi_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockEspblufiPlatform
    with MockPlatformInterfaceMixin
    implements EspblufiPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final EspblufiPlatform initialPlatform = EspblufiPlatform.instance;

  test('$MethodChannelEspblufi is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelEspblufi>());
  });

  test('getPlatformVersion', () async {
    Espblufi espblufiPlugin = Espblufi();
    MockEspblufiPlatform fakePlatform = MockEspblufiPlatform();
    EspblufiPlatform.instance = fakePlatform;

    expect(await espblufiPlugin.getPlatformVersion(), '42');
  });
}
