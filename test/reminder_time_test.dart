import 'package:flutter_test/flutter_test.dart';
import 'package:ato_memo/model.dart';

void main() {
  test('today after preferred time remains future', () {
    final now = DateTime(2026, 10, 4, 21);
    expect(quickTime('today', now, 20, 0), DateTime(2026, 10, 4, 22));
  });
  test('tomorrow crosses month and year', () {
    expect(
      quickTime('tomorrow', DateTime(2026, 12, 31, 23), 9, 30),
      DateTime(2027, 1, 1, 9, 30),
    );
  });
  test('weekend skips elapsed Saturday time', () {
    expect(
      quickTime('weekend', DateTime(2026, 10, 10, 21), 20, 0),
      DateTime(2026, 10, 17, 20),
    );
  });
  test('weekend on Sunday chooses next Saturday', () {
    expect(
      quickTime('weekend', DateTime(2026, 10, 4, 9), 20, 0),
      DateTime(2026, 10, 10, 20),
    );
  });
}
