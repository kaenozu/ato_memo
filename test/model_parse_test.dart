import 'package:flutter_test/flutter_test.dart';
import 'package:ato_memo/model.dart';

Map<dynamic, dynamic> validRow() => {
  'id': 1,
  'path': '/a.png',
  'title': 'Screenshot.png',
  'url': '',
  'ocr': '10/15まで',
  'ocrError': '',
  'created': 1,
  'due': 2,
  'completed': 0,
  'delivered': 0,
  'custom': false,
  'candidates': <int>[],
};

void main() {
  test('valid row parses', () {
    expect(Memo.fromMap(validRow())?.id, 1);
  });
  test('null id or empty path is skipped', () {
    final noId = validRow()..['id'] = -1;
    expect(Memo.fromMap(noId), isNull);
    final noPath = validRow()..['path'] = '';
    expect(Memo.fromMap(noPath), isNull);
    final nullPath = validRow()..['path'] = null;
    expect(Memo.fromMap(nullPath), isNull);
  });
  test('num/bool variants do not throw', () {
    final row = validRow()
      ..['completed'] = 123.0
      ..['custom'] = 1
      ..['candidates'] = [
        1000,
        '2000',
        'broken',
        null,
      ];
    final memo = Memo.fromMap(row);
    expect(memo?.done, isTrue);
    expect(memo?.custom, isTrue);
    expect(memo?.candidates.length, 2);
  });
  test('missing optional fields fall back', () {
    final row = <dynamic, dynamic>{
      'id': 7,
      'path': '/b.png',
      'candidates': null,
    };
    final memo = Memo.fromMap(row);
    expect(memo?.title, 'スクショ');
    expect(memo?.candidates, isEmpty);
  });
}
