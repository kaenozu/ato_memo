import 'dart:io';
import 'package:flutter/material.dart';
import 'controller.dart';
import 'model.dart';
import 'detail_page.dart';
import 'settings_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.controller});
  final MemoController controller;
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int tab = 0;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller;
      final active = c.items.where((m) => !m.done).toList();
      final shown = c.items
          .where(
            (m) => tab == 2 ? m.done : !m.done && (tab == 0 || m.delivered),
          )
          .toList();
      final scheduled = active.where((m) => !m.delivered).toList();
      final next = scheduled.isEmpty ? null : scheduled.first.due;
      return Scaffold(
        appBar: AppBar(
          title: const Text(
            'あとメモ',
            style: TextStyle(fontWeight: FontWeight.w700),
          ),
          actions: [
            IconButton(
              tooltip: '設定',
              icon: const Icon(Icons.tune),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => SettingsPage(controller: c),
                ),
              ),
            ),
          ],
        ),
        body: !c.loaded
            ? Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 16),
                    const Text('読み込んでいます'),
                    TextButton(onPressed: c.refresh, child: const Text('再試行')),
                  ],
                ),
              )
            : RefreshIndicator(
                onRefresh: c.refresh,
                child: CustomScrollView(
                  slivers: [
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '忘れる前に、もう一度。',
                              style: Theme.of(context).textTheme.headlineSmall
                                  ?.copyWith(fontWeight: FontWeight.w700),
                            ),
                            const SizedBox(height: 8),
                            const Text('スクショした情報を、ちょうどいい時に。'),
                            const SizedBox(height: 24),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(20),
                              decoration: BoxDecoration(
                                color: Theme.of(
                                  context,
                                ).colorScheme.primaryContainer,
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      const Icon(Icons.notifications_none),
                                      const SizedBox(width: 8),
                                      Text(
                                        c.settings['notify'] == false
                                            ? '通知はお休み中'
                                            : '次の見返す時間',
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 10),
                                  Text(
                                    c.settings['notify'] == false
                                        ? '設定から再開できます'
                                        : next == null
                                        ? c.settings['auto'] == true
                                              ? '対象の画像を自動で探します'
                                              : '自動取り込みではじめよう'
                                        : dateLabel(next),
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge
                                        ?.copyWith(fontWeight: FontWeight.w700),
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    '見返す画像 ${active.length}枚・自動取り込み ${c.settings['auto'] == true ? 'ON' : 'OFF'}',
                                  ),
                                ],
                              ),
                            ),
                            if (c.settings['auto'] != true)
                              Padding(
                                padding: const EdgeInsets.only(top: 12),
                                child: ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  leading: const Icon(Icons.auto_awesome),
                                  title: const Text('手動登録なしで見返す'),
                                  subtitle: const Text(
                                    '既存のスクショも、これから撮るスクショも自動で保存・通知',
                                  ),
                                  trailing: FilledButton(
                                    onPressed: c.busy
                                        ? null
                                        : () => SettingsPage(
                                            controller: c,
                                          ).enableAuto(context, true),
                                    child: const Text('自動取り込みをON'),
                                  ),
                                ),
                              ),
                            if (c.settings['notify'] != false &&
                                c.settings['notificationsAllowed'] == false)
                              Padding(
                                padding: const EdgeInsets.only(top: 12),
                                child: ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  leading: const Icon(
                                    Icons.notifications_off_outlined,
                                  ),
                                  title: const Text('通知の許可が必要です'),
                                  trailing: TextButton(
                                    onPressed: c.busy
                                        ? null
                                        : () =>
                                              c.action('requestNotifications'),
                                    child: const Text('許可する'),
                                  ),
                                ),
                              ),
                            if (c.importing || c.busy)
                              const Padding(
                                padding: EdgeInsets.only(top: 16),
                                child: LinearProgressIndicator(),
                              ),
                            const SizedBox(height: 20),
                            Wrap(
                              spacing: 8,
                              children: [
                                for (final entry in [
                                  (0, '見返す'),
                                  (1, '通知済み'),
                                  (2, '完了'),
                                ])
                                  ChoiceChip(
                                    label: Text(entry.$2),
                                    selected: tab == entry.$1,
                                    onSelected: (_) =>
                                        setState(() => tab = entry.$1),
                                  ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                    if (shown.isEmpty)
                      SliverFillRemaining(
                        hasScrollBody: false,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(24, 16, 24, 100),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                tab == 2
                                    ? Icons.check_circle_outline
                                    : Icons.photo_library_outlined,
                                size: 56,
                                color: Theme.of(context).colorScheme.primary,
                              ),
                              const SizedBox(height: 16),
                              Text(
                                tab == 2
                                    ? '完了した画像はここに'
                                    : tab == 1
                                    ? '通知済みの画像はまだありません'
                                    : '「あとで見たい」をここに',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              const SizedBox(height: 12),
                              Text(
                                tab == 0
                                    ? c.settings['auto'] == true
                                          ? '対象画像を自動で取り込み、設定した時刻に通知します。\n写真やダウンロード画像も対象にするには、\n設定の「取り込む画像」を「すべての画像」に変更してください。'
                                          : '自動取り込みをONにすると、端末のスクショを\n手動登録なしで取り込み、設定した時刻に通知します。\n個別の画像は共有や下のボタンから追加できます。'
                                    : '画像は開くだけでは完了になりません。',
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ),
                        ),
                      )
                    else
                      SliverPadding(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 100),
                        sliver: SliverList.separated(
                          itemCount: shown.length,
                          separatorBuilder: (_, _) =>
                              const SizedBox(height: 12),
                          itemBuilder: (context, index) {
                            final memo = shown[index];
                            return Card(
                              color: Theme.of(
                                context,
                              ).colorScheme.surfaceContainerLow,
                              child: InkWell(
                                borderRadius: BorderRadius.circular(12),
                                onTap: () => Navigator.push(
                                  context,
                                  MaterialPageRoute<void>(
                                    builder: (_) =>
                                        DetailPage(controller: c, id: memo.id),
                                  ),
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: Row(
                                    children: [
                                      ClipRRect(
                                        borderRadius: BorderRadius.circular(8),
                                        child: Image.file(
                                          File(memo.path),
                                          width: 76,
                                          height: 88,
                                          fit: BoxFit.cover,
                                          cacheWidth: 240,
                                          errorBuilder: (_, _, _) =>
                                              const SizedBox(
                                                width: 76,
                                                height: 88,
                                                child: Icon(
                                                  Icons.broken_image_outlined,
                                                ),
                                              ),
                                        ),
                                      ),
                                      const SizedBox(width: 16),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              memo.title,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(
                                                fontWeight: FontWeight.w600,
                                              ),
                                            ),
                                            const SizedBox(height: 8),
                                            Text(
                                              memo.done
                                                  ? '完了'
                                                  : memo.delivered
                                                  ? '通知済み・見返してみよう'
                                                  : dateLabel(memo.due),
                                            ),
                                            const SizedBox(height: 4),
                                            Text(
                                              memo.custom
                                                  ? '個別の予約'
                                                  : '設定の時刻でまとめ通知',
                                              style: Theme.of(
                                                context,
                                              ).textTheme.bodySmall,
                                            ),
                                          ],
                                        ),
                                      ),
                                      const Icon(Icons.chevron_right),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                  ],
                ),
              ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: c.busy ? null : () => c.action('pickImages'),
          icon: const Icon(Icons.add_photo_alternate_outlined),
          label: const Text('画像を追加'),
        ),
      );
    },
  );
}
