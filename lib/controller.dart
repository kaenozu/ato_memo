import 'package:flutter/foundation.dart';
import 'native_bridge.dart';
import 'model.dart';

class MemoController extends ChangeNotifier {
  final NativeBridge bridge;
  MemoController(this.bridge);
  List<Memo> items = [];
  Map<dynamic, dynamic> settings = {};
  bool loaded = false, importing = false, busy = false;
  String? error;
  int? openId, scheduleId;
  bool openHome = false;
  bool _refreshing = false, _again = false;
  Future<void> refresh() async {
    if (_refreshing) {
      _again = true;
      return;
    }
    _refreshing = true;
    try {
      do {
        _again = false;
        final data = await bridge.call<Map<dynamic, dynamic>>('snapshot');
        if (data == null) throw StateError('データを読み込めませんでした');
        items = (data['items'] as List)
            .map((m) => Memo.fromMap(m as Map))
            .toList();
        settings = data['settings'] as Map;
        importing = data['importing'] == true;
        final id = data['openId'] as int;
        if (id == -2) openHome = true;
        if (id > 0) openId = id;
        final schedule = data['scheduleId'] as int;
        if (schedule > 0) scheduleId = schedule;
        final importError = data['importError'] as String;
        if (importError.isNotEmpty) error = importError;
        loaded = true;
        notifyListeners();
      } while (_again);
    } catch (e) {
      error = e.toString();
      notifyListeners();
    } finally {
      _refreshing = false;
    }
  }

  Future<bool> action(String method, [Object? args]) async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      await bridge.call<Object?>(method, args);
      await refresh();
      return true;
    } catch (e) {
      error = e
          .toString()
          .replaceFirst(RegExp(r'^PlatformException\([^,]+, '), '')
          .split(', null')
          .first;
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<bool> save(Map<String, Object> values) =>
      action('saveSettings', values);
  Memo? item(int id) {
    for (final memo in items) {
      if (memo.id == id) return memo;
    }
    return null;
  }

  int get hour => settings['hour'] as int? ?? 20;
  int get minute => settings['minute'] as int? ?? 0;
}
