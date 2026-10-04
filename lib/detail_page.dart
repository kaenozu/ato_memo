import 'dart:io';
import 'package:flutter/material.dart';
import 'controller.dart';
import 'model.dart';
import 'schedule_sheet.dart';

class DetailPage extends StatefulWidget {
  const DetailPage({
    super.key,
    required this.controller,
    required this.id,
    this.openSchedule = false,
  });
  final MemoController controller;
  final int id;
  final bool openSchedule;
  @override
  State<DetailPage> createState() => _DetailPageState();
}

class _DetailPageState extends State<DetailPage> {
  @override
  void initState() {
    super.initState();
    if (widget.openSchedule) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final memo = widget.controller.item(widget.id);
        if (mounted && memo != null) {
          showSchedule(context, widget.controller, memo);
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller;
      final memo = c.item(widget.id);
      if (memo == null) {
        return Scaffold(
          appBar: AppBar(title: const Text('画像')),
          body: const Center(child: Text('この画像は削除されています')),
        );
      }
      return Scaffold(
        appBar: AppBar(
          title: const Text('見返す'),
          actions: [
            IconButton(
              tooltip: '削除',
              icon: const Icon(Icons.delete_outline),
              onPressed: c.busy
                  ? null
                  : () async {
                      final confirmed = await showDialog<bool>(
                        context: context,
                        builder: (context) => AlertDialog(
                          title: const Text('この画像を削除しますか？'),
                          content: const Text(
                            'あとメモ内のコピーと予約を削除します。元の画像は削除しません。',
                          ),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.pop(context, false),
                              child: const Text('キャンセル'),
                            ),
                            FilledButton(
                              onPressed: () => Navigator.pop(context, true),
                              child: const Text('削除'),
                            ),
                          ],
                        ),
                      );
                      if (confirmed == true &&
                          await c.action('delete', memo.id) &&
                          context.mounted) {
                        Navigator.pop(context);
                      }
                    },
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          children: [
            Semantics(
              button: true,
              label: '画像を拡大',
              child: InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (context) => Scaffold(
                      backgroundColor: Colors.black,
                      appBar: AppBar(
                        backgroundColor: Colors.black,
                        foregroundColor: Colors.white,
                        title: const Text('画像'),
                      ),
                      body: Center(
                        child: InteractiveViewer(
                          minScale: 0.5,
                          maxScale: 6,
                          child: Image.file(
                            File(memo.path),
                            errorBuilder: (_, _, _) => const Text(
                              '画像を開けません',
                              style: TextStyle(color: Colors.white),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    height: 320,
                    color: Theme.of(context).colorScheme.surfaceContainer,
                    child: Image.file(
                      File(memo.path),
                      fit: BoxFit.contain,
                      cacheWidth: 1200,
                      errorBuilder: (_, _, _) =>
                          const Center(child: Text('画像を開けません')),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            const Text('タップで拡大・ピンチでズーム', textAlign: TextAlign.center),
            const SizedBox(height: 24),
            Text(memo.title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 16),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(
                memo.done
                    ? Icons.check_circle_outline
                    : Icons.notifications_none,
              ),
              title: Text(memo.done ? '完了しました' : dateLabel(memo.due)),
              subtitle: Text(
                memo.done
                    ? '完了から30日後にアプリ内のコピーを削除します'
                    : memo.delivered
                    ? '通知済みです'
                    : 'この日時に見返す通知',
              ),
              trailing: TextButton(
                onPressed: c.busy ? null : () => showSchedule(context, c, memo),
                child: Text(memo.done ? '再予約' : '変更'),
              ),
            ),
            const SizedBox(height: 16),
            if (!memo.done) ...[
              FilledButton.icon(
                onPressed: c.busy
                    ? null
                    : () async {
                        if (await c.action('complete', memo.id) &&
                            context.mounted) {
                          Navigator.pop(context);
                        }
                      },
                icon: const Icon(Icons.check),
                label: const Text('見返した・完了'),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: c.busy
                    ? null
                    : () async {
                        final tomorrow = quickTime(
                          'tomorrow',
                          DateTime.now(),
                          c.hour,
                          c.minute,
                        );
                        if (await c.action('schedule', {
                              'id': memo.id,
                              'due': tomorrow.millisecondsSinceEpoch,
                            }) &&
                            context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('${dateLabel(tomorrow)}に再通知します'),
                            ),
                          );
                        }
                      },
                icon: const Icon(Icons.snooze),
                label: const Text('明日また通知'),
              ),
            ],
            if (memo.url.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: OutlinedButton.icon(
                  onPressed: c.busy
                      ? null
                      : () => c.action('openUrl', memo.url),
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('元のリンクを開く'),
                ),
              ),
            if (memo.ocrError.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(memo.ocrError),
                    TextButton(
                      onPressed: c.busy
                          ? null
                          : () => c.action('retryOcr', memo.id),
                      child: const Text('文字を読み取り直す'),
                    ),
                  ],
                ),
              ),
            if (memo.ocr.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  title: const Text('読み取った文字'),
                  subtitle: const Text('端末内で処理しています'),
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: SelectableText(memo.ocr),
                    ),
                  ],
                ),
              ),
            if (c.busy) const LinearProgressIndicator(),
          ],
        ),
      );
    },
  );
}
