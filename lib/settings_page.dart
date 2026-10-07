import 'package:flutter/material.dart';
import 'controller.dart';
import 'model.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.controller});
  final MemoController controller;
  Future<void> enableAuto(BuildContext context, bool value) async {
    if (!value) {
      await controller.save({'auto': false});
      return;
    }
    if (controller.settings['mediaAllowed'] != true) {
      final yes = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('画像を自動で取り込む'),
          content: const Text(
            '端末にある画像と、これから保存する画像を自動で取り込みます。写真への「すべて許可」が必要です。画像は端末内で処理し、外部に送信しません。',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('キャンセル'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('許可へ進む'),
            ),
          ],
        ),
      );
      if (yes != true) return;
      await controller.action('requestMedia');
      // 拒否・一部許可時は action が失敗する。最新状態で判定する (#10)。
      await controller.refresh();
    }
    if (controller.settings['mediaAllowed'] == true) {
      await controller.save({'auto': true});
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('自動取り込みには全画像へのアクセスが必要です。手動の画像追加は使えます。')),
      );
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final c = controller;
      final s = c.settings;
      return Scaffold(
        appBar: AppBar(title: const Text('設定')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            const _Heading('自動取り込み'),
            SwitchListTile(
              title: const Text('画像を自動で取り込む'),
              subtitle: Text(
                '既存画像も新しい画像も対象・${s['allImages'] == true ? 'すべての画像' : 'スクショだけ'}',
              ),
              value: s['auto'] == true,
              onChanged: c.busy ? null : (value) => enableAuto(context, value),
            ),
            if (c.busy)
              const ListTile(
                leading: SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(),
                ),
                title: Text('処理しています'),
                subtitle: Text('取り込みは枚数によって時間がかかります'),
              ),
            ListTile(
              title: const Text('取り込む画像'),
              subtitle: Text(
                s['allImages'] == true ? 'すべての画像（写真・ダウンロードも含む）' : 'スクショだけ',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: c.busy
                  ? null
                  : () async {
                      final all = await showDialog<bool>(
                        context: context,
                        builder: (context) => SimpleDialog(
                          title: const Text('取り込む画像'),
                          children: [
                            SimpleDialogOption(
                              onPressed: () => Navigator.pop(context, false),
                              child: const Padding(
                                padding: EdgeInsets.all(8),
                                child: Text('スクショだけ'),
                              ),
                            ),
                            SimpleDialogOption(
                              onPressed: () => Navigator.pop(context, true),
                              child: const Padding(
                                padding: EdgeInsets.all(8),
                                child: Text('すべての画像'),
                              ),
                            ),
                          ],
                        ),
                      );
                      if (all != null) await c.save({'allImages': all});
                    },
            ),
            ListTile(
              title: const Text('画像のアクセス'),
              subtitle: Text(
                s['mediaAllowed'] == true ? '全画像へのアクセス許可あり' : '全画像へのアクセス許可なし',
              ),
              trailing: TextButton(
                onPressed: c.busy ? null : () => c.action('appSettings'),
                child: const Text('端末設定'),
              ),
            ),
            ListTile(
              title: const Text('過去の画像を取り込む'),
              subtitle: const Text('選んだ対象の既存画像もまとめて追加'),
              leading: const Icon(Icons.history),
              onTap: c.busy
                  ? null
                  : () async {
                      if (s['mediaAllowed'] != true) {
                        await c.action('requestMedia');
                        await c.refresh();
                      }
                      if (!context.mounted ||
                          c.settings['mediaAllowed'] != true) {
                        return;
                      }
                      final yes = await showDialog<bool>(
                        context: context,
                        builder: (context) => AlertDialog(
                          title: const Text('過去の画像も追加しますか？'),
                          content: Text(
                            '${c.settings['allImages'] == true ? 'すべての画像' : 'スクショ'}を取り込み、設定の時刻で通知を予約します。枚数が多いと時間と保存容量が必要です。',
                          ),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.pop(context, false),
                              child: const Text('キャンセル'),
                            ),
                            FilledButton(
                              onPressed: () => Navigator.pop(context, true),
                              child: const Text('取り込む'),
                            ),
                          ],
                        ),
                      );
                      if (yes == true) await c.action('scan', true);
                    },
            ),
            if ((s['lastError'] as String? ?? '').isNotEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(s['lastError'] as String),
              ),
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('端末の省電力設定により取り込みが遅れる場合があります。スクショは保存先やファイル名から判定します。'),
            ),
            const Divider(),
            const _Heading('見返す通知'),
            SwitchListTile(
              title: const Text('通知する'),
              subtitle: const Text('OFFでも画像と予約は保存します'),
              value: s['notify'] != false,
              onChanged: c.busy ? null : (value) => c.save({'notify': value}),
            ),
            ListTile(
              title: const Text('まとめ通知の時刻'),
              subtitle: Text(clock(c.hour, c.minute)),
              trailing: const Icon(Icons.schedule),
              onTap: c.busy
                  ? null
                  : () async {
                      final time = await showTimePicker(
                        context: context,
                        initialTime: TimeOfDay(hour: c.hour, minute: c.minute),
                      );
                      if (time != null) {
                        await c.save({
                          'hour': time.hour,
                          'minute': time.minute,
                        });
                      }
                    },
            ),
            ListTile(
              title: const Text('通知の頻度'),
              trailing: DropdownButton<bool>(
                value: s['weekly'] == true,
                items: const [
                  DropdownMenuItem(value: false, child: Text('毎日')),
                  DropdownMenuItem(value: true, child: Text('週1回')),
                ],
                onChanged: c.busy
                    ? null
                    : (value) {
                        if (value != null) c.save({'weekly': value});
                      },
              ),
            ),
            if (s['weekly'] == true)
              ListTile(
                title: const Text('通知する曜日'),
                trailing: DropdownButton<int>(
                  value: s['weekday'] as int? ?? 6,
                  items: [
                    for (var i = 1; i <= 7; i++)
                      DropdownMenuItem(
                        value: i,
                        child: Text(
                          '${['月', '火', '水', '木', '金', '土', '日'][i - 1]}曜日',
                        ),
                      ),
                  ],
                  onChanged: c.busy
                      ? null
                      : (value) {
                          if (value != null) c.save({'weekday': value});
                        },
                ),
              ),
            ListTile(
              title: const Text('取り込み後の通知'),
              trailing: DropdownButton<int>(
                value: s['delayDays'] as int? ?? 1,
                items: const [
                  DropdownMenuItem(value: 1, child: Text('翌日以降')),
                  DropdownMenuItem(value: 0, child: Text('当日から')),
                ],
                onChanged: c.busy
                    ? null
                    : (value) {
                        if (value != null) c.save({'delayDays': value});
                      },
              ),
            ),
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                '変更は、まだ通知していない自動予約にも反映します。画像ごとに指定した日時はそのままです。通知は対象画像をまとめて1件でお知らせします。',
              ),
            ),
            ListTile(
              title: const Text('端末の通知許可'),
              subtitle: Text(
                s['notificationsAllowed'] == true ? '許可されています' : '許可されていません',
              ),
              trailing: TextButton(
                onPressed: c.busy
                    ? null
                    : () => c.action(
                        s['notificationsAllowed'] == true
                            ? 'appSettings'
                            : 'requestNotifications',
                      ),
                child: const Text('変更'),
              ),
            ),
            ListTile(
              title: const Text('正確な時刻の通知'),
              subtitle: Text(
                s['exactAllowed'] == true
                    ? '時刻指定へのアクセス許可あり'
                    : '省電力などで通知が遅れる場合があります',
              ),
              trailing: TextButton(
                onPressed: c.busy ? null : () => c.action('exactSettings'),
                child: const Text('端末設定'),
              ),
            ),
            const Divider(),
            const _Heading('画像とプライバシー'),
            const ListTile(
              leading: Icon(Icons.lock_outline),
              title: Text('画像も文字の読み取りも端末内'),
              subtitle: Text('画像・読み取った文字を外部に送信しません。通知には画像や本文を表示しません。'),
            ),
            const ListTile(
              leading: Icon(Icons.photo_outlined),
              title: Text('元の画像は変更しません'),
              subtitle: Text(
                'アプリ内にコピーを保存します。完了から30日後にコピーだけを削除します。アプリを削除するとコピーと予約も消えます。',
              ),
            ),
            ListTile(
              title: Text(
                'あとメモ ${c.version.isNotEmpty ? c.version : '0.1.1'}',
              ),
              subtitle: const Text('スクショを、忘れる前にもう一度。'),
            ),
            if (c.busy) const LinearProgressIndicator(),
          ],
        ),
      );
    },
  );
}

class _Heading extends StatelessWidget {
  const _Heading(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
    child: Text(
      text,
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        color: Theme.of(context).colorScheme.primary,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}
