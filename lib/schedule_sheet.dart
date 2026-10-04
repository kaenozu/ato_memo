import 'package:flutter/material.dart';
import 'controller.dart';
import 'model.dart';

Future<void> showSchedule(
  BuildContext context,
  MemoController controller,
  Memo memo,
) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) => ScheduleSheet(controller: controller, memo: memo),
);

class ScheduleSheet extends StatefulWidget {
  const ScheduleSheet({
    super.key,
    required this.controller,
    required this.memo,
  });
  final MemoController controller;
  final Memo memo;
  @override
  State<ScheduleSheet> createState() => _ScheduleSheetState();
}

class _ScheduleSheetState extends State<ScheduleSheet> {
  late DateTime selected;
  bool saving = false;
  @override
  void initState() {
    super.initState();
    selected = widget.memo.due.isAfter(DateTime.now())
        ? widget.memo.due
        : quickTime(
            'tomorrow',
            DateTime.now(),
            widget.controller.hour,
            widget.controller.minute,
          );
  }

  Future<void> customTime() async {
    final now = DateTime.now();
    final initial = selected.isBefore(now) ? now : selected;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 10, 12, 31),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(selected),
    );
    if (time != null && mounted) {
      setState(
        () => selected = DateTime(
          date.year,
          date.month,
          date.day,
          time.hour,
          time.minute,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final future = selected.isAfter(DateTime.now());
    return SafeArea(
      child: SingleChildScrollView(
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            24,
            0,
            24,
            24 + MediaQuery.viewInsetsOf(context).bottom,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'いつ見返す？',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final option in [
                    ('today', '今日'),
                    ('tomorrow', '明日'),
                    ('weekend', '週末'),
                  ])
                    ActionChip(
                      label: Text(option.$2),
                      onPressed: saving
                          ? null
                          : () => setState(
                              () => selected = quickTime(
                                option.$1,
                                DateTime.now(),
                                widget.controller.hour,
                                widget.controller.minute,
                              ),
                            ),
                    ),
                  ActionChip(
                    avatar: const Icon(Icons.calendar_today, size: 18),
                    label: const Text('日時指定'),
                    onPressed: saving ? null : customTime,
                  ),
                ],
              ),
              if (widget.memo.candidates.isNotEmpty) ...[
                const SizedBox(height: 24),
                const Text(
                  '画像から見つけた日時',
                  style: TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 4),
                const Text('締切や開演時刻の候補です。通知する日時を選んでください。'),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final date in widget.memo.candidates)
                      ActionChip(
                        label: Text('${date.year}年 ${dateLabel(date)}'),
                        onPressed: saving
                            ? null
                            : () => setState(() => selected = date),
                      ),
                  ],
                ),
              ],
              const SizedBox(height: 24),
              Text(
                dateLabel(selected),
                style: Theme.of(context).textTheme.titleLarge,
              ),
              if (!future)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text('過去の日時です。これからの通知日時を選んでください。'),
                ),
              if (widget.controller.settings['notify'] == false ||
                  widget.controller.settings['notificationsAllowed'] == false)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text('予約は保存できます。通知を受け取るには設定で通知を有効にしてください。'),
                ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: saving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.notifications_active_outlined),
                  label: const Text('この日時に通知する'),
                  onPressed: !future || saving
                      ? null
                      : () async {
                          setState(() => saving = true);
                          final ok = await widget.controller
                              .action('schedule', {
                                'id': widget.memo.id,
                                'due': selected.millisecondsSinceEpoch,
                              });
                          if (context.mounted) {
                            if (ok) {
                              Navigator.pop(context);
                            } else {
                              setState(() => saving = false);
                            }
                          }
                        },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
