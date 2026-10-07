int _toInt(Object? value, int fallback) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

String _toString(Object? value, [String fallback = '']) {
  if (value == null) return fallback;
  if (value is String) return value;
  return value.toString();
}

bool _toBool(Object? value, [bool fallback = false]) {
  if (value is bool) return value;
  if (value is num) return value != 0;
  if (value is String) {
    final v = value.toLowerCase();
    if (v == 'true' || v == '1') return true;
    if (v == 'false' || v == '0') return false;
  }
  return fallback;
}

DateTime _toDateTime(Object? value, DateTime fallback) {
  final millis = value is num
      ? value.toInt()
      : value is String
      ? int.tryParse(value)
      : null;
  if (millis == null) return fallback;
  try {
    return DateTime.fromMillisecondsSinceEpoch(millis);
  } catch (_) {
    return fallback;
  }
}

class Memo {
  final int id;
  final String path, title, url, ocr, ocrError;
  final DateTime created, due;
  final bool done, delivered, custom;
  final List<DateTime> candidates;
  Memo._({
    required this.id,
    required this.path,
    required this.title,
    required this.url,
    required this.ocr,
    required this.ocrError,
    required this.created,
    required this.due,
    required this.done,
    required this.delivered,
    required this.custom,
    required this.candidates,
  });

  /// 不正行は null を返し、呼び出し側でスキップする（#6）。
  static Memo? fromMap(Map<dynamic, dynamic> m) {
    try {
      final now = DateTime.now();
      final id = _toInt(m['id'], -1);
      final path = _toString(m['path']);
      if (id <= 0 || path.isEmpty) return null;
      final rawCandidates = m['candidates'];
      final candidates = <DateTime>[];
      if (rawCandidates is List) {
        for (final n in rawCandidates) {
          final millis = n is num
              ? n.toInt()
              : n is String
              ? int.tryParse(n)
              : null;
          if (millis == null) continue;
          try {
            candidates.add(DateTime.fromMillisecondsSinceEpoch(millis));
          } catch (_) {
            // 単発の不正候補は無視する。
          }
        }
      }
      return Memo._(
        id: id,
        path: path,
        title: _toString(m['title'], 'スクショ'),
        url: _toString(m['url']),
        ocr: _toString(m['ocr']),
        ocrError: _toString(m['ocrError']),
        created: _toDateTime(m['created'], now),
        due: _toDateTime(m['due'], now),
        done: _toInt(m['completed'], 0) > 0,
        delivered: _toInt(m['delivered'], 0) > 0,
        custom: _toBool(m['custom']),
        candidates: candidates,
      );
    } catch (_) {
      return null;
    }
  }
}

String clock(int hour, int minute) =>
    '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
String dateLabel(DateTime value) =>
    '${value.month}/${value.day}（${['月', '火', '水', '木', '金', '土', '日'][value.weekday - 1]}）${clock(value.hour, value.minute)}';

DateTime quickTime(String key, DateTime now, int hour, int minute) {
  var day = DateTime(now.year, now.month, now.day);
  switch (key) {
    case 'today':
      final preferred = DateTime(day.year, day.month, day.day, hour, minute);
      return preferred.isAfter(now)
          ? preferred
          : now.add(const Duration(hours: 1));
    case 'tomorrow':
      day = DateTime(now.year, now.month, now.day + 1);
    case 'weekend':
      day = DateTime(
        now.year,
        now.month,
        now.day + (DateTime.saturday - now.weekday + 7) % 7,
      );
      if (!DateTime(day.year, day.month, day.day, hour, minute).isAfter(now)) {
        day = DateTime(day.year, day.month, day.day + 7);
      }
  }
  return DateTime(day.year, day.month, day.day, hour, minute);
}
