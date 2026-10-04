import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ato_memo/main.dart';
import 'package:ato_memo/native_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Map<String, Object> settings;
  late List<Map<String, Object>> items;
  late List<MethodCall> calls;
  setUp(() {
    settings = {
      'auto': false,
      'allImages': false,
      'notify': true,
      'hour': 20,
      'minute': 0,
      'delayDays': 1,
      'weekly': false,
      'weekday': 6,
      'mediaAllowed': true,
      'notificationsAllowed': true,
      'exactAllowed': false,
      'lastError': '',
    };
    items = [];
    calls = [];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(NativeBridge.channel, (call) async {
          calls.add(call);
          if (call.method == 'snapshot') {
            return {
              'items': items,
              'settings': settings,
              'openId': -1,
              'scheduleId': -1,
              'importing': false,
              'importError': '',
            };
          }
          if (call.method == 'saveSettings') {
            settings.addAll(Map<String, Object>.from(call.arguments as Map));
          }
          if (call.method == 'complete') {
            items[0]['completed'] = DateTime.now().millisecondsSinceEpoch;
          }
          return null;
        });
  });
  testWidgets('empty state explains both shared and automatic import', (
    tester,
  ) async {
    await tester.pumpWidget(const AtoMemoApp());
    await tester.pumpAndSettle();
    expect(find.text('「あとで見たい」をここに'), findsOneWidget);
    expect(find.text('画像を追加'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('partial media access keeps auto import disabled', (
    tester,
  ) async {
    settings['mediaAllowed'] = false;
    await tester.pumpWidget(const AtoMemoApp());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('設定'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('許可へ進む'));
    await tester.pumpAndSettle();
    expect(settings['auto'], false);
    expect(calls.where((call) => call.method == 'requestMedia'), hasLength(1));
    expect(tester.takeException(), isNull);
  });
  testWidgets('small phone and large text keep empty screen usable', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 1.8;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await tester.pumpWidget(const AtoMemoApp());
    await tester.pumpAndSettle();
    expect(find.text('画像を追加'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('auto and cadence settings save without extra save button', (
    tester,
  ) async {
    await tester.pumpWidget(const AtoMemoApp());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('設定'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile).first);
    await tester.pumpAndSettle();
    expect(settings['auto'], true);
    await tester.scrollUntilVisible(find.text('通知の頻度'), 250);
    await tester.pumpAndSettle();
    await tester.tap(find.text('毎日'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('週1回').last);
    await tester.pumpAndSettle();
    expect(settings['weekly'], true);
    expect(find.text('通知する曜日'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('view does not complete until explicit button', (tester) async {
    items.add({
      'id': 1,
      'path': '/missing.png',
      'title': 'Screenshot.png',
      'url': '',
      'ocr': '10/15まで',
      'ocrError': '',
      'created': 1,
      'due': DateTime.now().add(const Duration(days: 2)).millisecondsSinceEpoch,
      'completed': 0,
      'delivered': 0,
      'custom': false,
      'candidates': <int>[],
    });
    await tester.pumpWidget(const AtoMemoApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Screenshot.png'));
    await tester.pumpAndSettle();
    expect(calls.where((c) => c.method == 'complete'), isEmpty);
    await tester.scrollUntilVisible(find.text('見返した・完了'), 200);
    await tester.pumpAndSettle();
    await tester.tap(find.text('見返した・完了'));
    await tester.pumpAndSettle();
    expect(calls.where((c) => c.method == 'complete').single.arguments, 1);
    expect(tester.takeException(), isNull);
  });
}
