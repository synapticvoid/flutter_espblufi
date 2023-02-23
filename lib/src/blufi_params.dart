import 'package:flutter/foundation.dart';

@immutable
class BlufiParameter {
  static const int opModelNull = 0x00;
  static const int opModelSta = 0x01;
  static const int opModelSoftAp = 0x02;
  static const int opModelSStaSoftAp = 0x03;
}

class BlufiConfigureParams {
  final int opMode;
  final String? staSSID;
  final String? staPassword;

  BlufiConfigureParams({
    required this.opMode,
    this.staSSID,
    this.staPassword,
  });
}

