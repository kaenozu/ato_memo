import 'package:flutter/foundation.dart';
import 'native_bridge.dart';
import 'model.dart';

int _optInt(Object? value, int fallback) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

class MemoController extends ChangeNotifier {
  final NativeBridge bridge;
  MemoController(this.bridge);
  List<Memo> items = [];
  Map<dynamic, dynamic> settings = {};
  bool loaded = false, importing = false;
  String? error;
  int? openId, scheduleId;
  bool openHome = false;
  bool _refreshing = false, _again = false;
  final Set<String> _running = {};
  bool get busy => _running.isNotEmpty;
  bool isBusy([String? method]) =>
      method == null ? busy : _running.contains(method);
  bool get saving => isBusy('saveSettings');
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
        final raw = data['items'];
        if (raw is List) {
          items = [
            for (final m in raw)
              if (m is Map) Memo.fromMap(m),
          ].whereType<Memo>().toList();
        } else {
          items = [];
        }
        final rawSettings = data['settings'];
        settings = rawSettings is Map ? rawSettings : {};
        importing = data['importing'] == true;
        final id = _optInt(data['openId'], -1);
        if (id == -2) openHome = true;
        if (id > 0) openId = id;
        final schedule = _optInt(data['scheduleId'], -1);
        if (schedule > 0) scheduleId = schedule;
        final importError = data['importError'];
        if (importError is String && importError.isNotEmpty) {
          error = importError;
        }
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
    _running.add(method);
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
      _running.remove(method);
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

  int get hour => settings['hour'] is num
      ? (settings['hour'] as num).toInt()
      : 20;
  int get minute => settings['minute'] is num
      ? (settings['minute'] as num).toInt()
      : 0;
  String get version => settings['version'] is String
      ? settings['version'] as String
      : '';
}
