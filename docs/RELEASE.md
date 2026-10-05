# 配布手順

## 現在の状態

2026-10-05時点のバージョンは0.1.1+2。正式署名のrelease APKとAABの検証記録は[VERIFICATION.md](VERIFICATION.md)にあります。GitHub ReleasesでのAPK配布とGoogle Play公開は未実施です。

## 署名設定

既存版を更新する場合は、保管済みの署名鍵と同じ証明書を使ってください。新しい鍵へ置き換えると、直接配布したAPKを上書き更新できません。アンインストールするとアプリ内の画像・予約・設定が消えるため、更新確認はデータを保持したまま行います。

署名鍵はリポジトリ外の安全な場所に保存し、別の安全な場所にもバックアップします。`android/key.properties`に次の設定を作成してください。これは記入例であり、実際のパスワードはローカルのファイルにだけ記入します。

```properties
storeFile=C:/private/ato-memo-upload.jks
storePassword=<保管済みのキーストアのパスワード>
keyAlias=<保管済みの鍵のエイリアス>
keyPassword=<保管済みの鍵のパスワード>
```

`key.properties`、`.jks`、`.keystore`はGitの除外対象です。GitHubのコメント、ログ、添付ファイルにも掲載しないでください。CIへ署名秘密情報を登録する必要はありません。

現在のビルド設定は`key.properties`がない場合、releaseも開発用署名になります。`release`という名前だけで配布用と判断せず、証明書を確認してください。

## ビルドと成果物

Flutter 3.44.0 / Dart 3.12.0、JDK 17、Android SDKを用意します。署名設定が存在することを確認してから実行します。

```powershell
if (!(Test-Path android/key.properties)) { throw '配布用の署名設定がありません' }
flutter pub get --enforce-lockfile
flutter analyze
flutter test
cd android
.\gradlew.bat :app:testDebugUnitTest
cd ..
flutter build apk --release --split-per-abi
flutter build appbundle --release
```

コマンドが失敗した場合は、そこで作業を止めて原因を修正してください。

| 成果物 | 出力先 | 用途 |
| --- | --- | --- |
| arm64 APK | `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk` | 通常のスマートフォンへの直接インストール |
| ARM 32bit APK | `build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk` | 対応する旧端末 |
| x86_64 APK | `build/app/outputs/flutter-apk/app-x86_64-release.apk` | 対応するエミュレーターなど |
| AAB | `build/app/outputs/bundle/release/app-release.aab` | Google Playへのアップロード用 |

AABは端末へ直接インストールするAPKではありません。更新時は`pubspec.yaml`のビルド番号（`+`の後）を増やします。

## 配布前の確認

1. Android SDK Build Toolsの`apksigner verify --verbose --print-certs <APKのパス>`で署名を検証し、SHA-256証明書フィンガープリントを保管済みの配布版と比較します。開発用署名でないことも確認します。
2. AABは`jarsigner -verify -verbose -certs <AABのパス>`で検証し、`keytool -printcert -jarfile <AABのパス>`でアップロード証明書を比較します。Play App Signingを使う場合、アップロード証明書と端末向け配布証明書は異なることがあります。
3. SDKの`apkanalyzer manifest permissions <APKのパス>`でINTERNET権限がないことを確認します。
4. 専用の検証端末で、旧版からデータを保持した更新、通信OFFでの画像追加と日本語OCR、既存画像の自動取り込み、通知の表示・タップを確認します。端末テストはデータをリセットするため、普段使うデータへ実行しません。
5. 実機の画面OFF・省電力・再起動後の挙動は別途確認し、確認した環境と結果を`VERIFICATION.md`へ記録します。

GitHub Releasesへ配布する際は、バージョン、対応端末、証明書を確認したAPK、SHA-256ファイルハッシュ、既知の制限を揃えます。Google Play公開は別の作業として実施します。

## CIの範囲

GitHub Actionsは解析・Flutterテスト・Android単体テスト・release APKビルドを実行します。署名鍵を使わないためAPKは開発用署名です。APKの自動配布は行わず、Android単体テストのレポートを7日間保存します。エミュレーターの実動作テストと実機検証はCIに含まれません。
