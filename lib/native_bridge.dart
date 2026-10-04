import 'package:flutter/services.dart';

class NativeBridge {
  static const channel = MethodChannel('ato_memo/native');
  Future<T?> call<T>(String method, [Object? args]) =>
      channel.invokeMethod<T>(method, args);
}
