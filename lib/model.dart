class Memo {
  final int id;
  final String path, title, url, ocr, ocrError;
  final DateTime created, due;
  final bool done, delivered, custom;
  final List<DateTime> candidates;
  Memo.fromMap(Map<dynamic, dynamic> m)
    : id = m['id'] as int,
      path = m['path'] as String,
      title = m['title'] as String,
      url = m['url'] as String,
      ocr = m['ocr'] as String,
      ocrError = m['ocrError'] as String,
      created = DateTime.fromMillisecondsSinceEpoch(m['created'] as int),
      due = DateTime.fromMillisecondsSinceEpoch(m['due'] as int),
      done = (m['completed'] as int) > 0,
      delivered = (m['delivered'] as int) > 0,
      custom = m['custom'] as bool,
      candidates = (m['candidates'] as List)
          .map((n) => DateTime.fromMillisecondsSinceEpoch(n as int))
          .toList();
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
