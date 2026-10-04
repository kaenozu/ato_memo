# MVP受入確認

2026-10-04、Flutter 3.44.0 / Dart 3.12.0、Android 15 (API 35)、専用AVD AtoMemo_API35で検証。

## 確認済み
- Flutter単体・画面テスト9件: 今日・明日・週末の日付境界、空画面、設定の保存、明示的な完了操作、一部画像のみの権限、320px幅と文字倍率1.8。
- Kotlin単体テスト6件: 日次・週次、過ぎた時刻、無効な日付と価格の除外、全角、年をまたぐ候補。
- Android実動作テスト10件 (MvpDeviceTest): 新規スクショ限定、重複・削除後の再取り込み防止、全画像と過去取り込み、コピーが元画像削除後も残る、日本語OCR、元URL、個別予約保持、通知本文のプライバシー、実通知のタップから画像表示、完了、通知OFF、30日経過後のコピーのみ削除、共有から予約画面、複数画像の共有、まとめ通知から一覧、OCR初期化失敗時も画像と予約を保持。
- Wi-FiをOFFにしてAndroid実動作テストを実行。日本語OCRモデルはアプリ内のローカルモデルとして起動。
- ホーム画面へ戻ってアプリプロセスを終了し、新しいScreenshot_auto.pngをMediaStoreに追加。アプリ画面を開く前にSQLiteに画像・OCR・日時候補が保存されていることを確認。ジョブ71/72が登録されていることも確認。
- Androidの画像選択画面からクーポンのテスト画像を選び、予約画面に「今日・明日・週末・日時指定」と「2026年10/15（木）19:00」の候補が出ることを確認。
- flutter analyze: 指摘なし。
- 実際に端末を再起動し、保存済みの予約と同じdue値のRTC_WAKEUPアラーム、MediaImportJobの71/72が、アプリを開く前に再登録されていることを確認。

- 配布用release APKをインストールし、通信OFFでクーポン画像の表示と日本語OCRによる日時候補を確認。R8でML Kit初期化部品を保持する設定を追加済み。最終APKの権限一覧にINTERNETがないことを確認。
- 再起動後の予約時刻に実通知「見返す画像が1枚あります」が届くことを確認。

## 検証の再実行
通常のAndroidテストと、状態を残す再起動準備は分けて実行する。

```powershell
flutter test
flutter analyze
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
cd ..
adb install -r build/app/outputs/apk/debug/app-debug.apk
adb install -r build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class jp.neoenox.ato_memo.MvpDeviceTest jp.neoenox.ato_memo.test/androidx.test.runner.AndroidJUnitRunner
```

テストはインストールしたアプリのデータベースと設定をリセットする。専用エミュレーターで実行し、普段使う端末の保存データに対して実行しない。

再起動シナリオはRebootScenarioTestで150秒後の予約と自動取り込みジョブを作ってから、adb rebootで実際に再起動し、再登録と通知を確認する。

## 適用範囲
エミュレーター上のMVP検証。実機メーカー固有の省電力動作、複数Androidバージョン、Google Playへの公開はこの検証に含まれない。日付候補は単純なパターン抽出であり、意味理解や締切の確定はしない。
