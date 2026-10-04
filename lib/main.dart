import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'controller.dart';
import 'native_bridge.dart';
import 'home_page.dart';
import 'detail_page.dart';

void main() => runApp(const AtoMemoApp());

class AtoMemoApp extends StatefulWidget {
  const AtoMemoApp({super.key, this.controller});
  final MemoController? controller;
  @override
  State<AtoMemoApp> createState() => _AtoMemoAppState();
}

class _AtoMemoAppState extends State<AtoMemoApp> with WidgetsBindingObserver {
  late final MemoController controller;
  final navigator = GlobalKey<NavigatorState>();
  int? _openingId;
  @override
  void initState() {
    super.initState();
    controller = widget.controller ?? MemoController(NativeBridge());
    WidgetsBinding.instance.addObserver(this);
    controller.addListener(_changed);
    NativeBridge.channel.setMethodCallHandler((call) async {
      if (call.method == 'changed') await controller.refresh();
    });
    controller.refresh();
  }

  void _changed() {
    if (controller.openHome) {
      controller.openHome = false;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) navigator.currentState?.popUntil((route) => route.isFirst);
      });
    }
    final error = controller.error;
    if (error != null && error.isNotEmpty) {
      controller.error = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && navigator.currentContext != null) {
          ScaffoldMessenger.of(
            navigator.currentContext!,
          ).showSnackBar(SnackBar(content: Text(error)));
        }
      });
    }
    final id = controller.openId;
    if (id == null || _openingId != null) return;
    controller.openId = null;
    final schedule = controller.scheduleId == id;
    controller.scheduleId = null;
    _openingId = id;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (mounted && navigator.currentState != null) {
        navigator.currentState!.popUntil((route) => route.isFirst);
        await navigator.currentState!.push(
          MaterialPageRoute<void>(
            builder: (_) => DetailPage(
              controller: controller,
              id: id,
              openSchedule: schedule,
            ),
          ),
        );
      }
      _openingId = null;
      if (mounted) _changed();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) controller.refresh();
  }

  @override
  void dispose() {
    controller.removeListener(_changed);
    WidgetsBinding.instance.removeObserver(this);
    NativeBridge.channel.setMethodCallHandler(null);
    if (widget.controller == null) controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    navigatorKey: navigator,
    debugShowCheckedModeBanner: false,
    title: 'あとメモ',
    locale: const Locale('ja'),
    supportedLocales: const [Locale('ja')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xff236a5b),
        surface: const Color(0xfffaf9f5),
      ),
      scaffoldBackgroundColor: const Color(0xfffaf9f5),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xfffaf9f5),
        centerTitle: false,
      ),
      cardTheme: const CardThemeData(elevation: 0, margin: EdgeInsets.zero),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
      ),
    ),
    home: HomePage(controller: controller),
  );
}
