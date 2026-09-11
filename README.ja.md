# Health Data Sync

[English](README.md) | 日本語

[![CI](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml/badge.svg)](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml)
![Version](https://img.shields.io/badge/version-0.1.5-0f766e)
![Android](https://img.shields.io/badge/Android-Health%20Connect-3ddc84)
![Server](https://img.shields.io/badge/API-.NET%208-512bd4)
[![License: AGPL-3.0-only](https://img.shields.io/badge/License-AGPL--3.0--only-blue.svg)](LICENSE)

**Health Data Sync**は、Health Connectのデータを確認し、CSV／ZIPとしてエクスポートできるローカルファーストのAndroidアプリです。必要な場合に限り、正規化したデータを小規模なセルフホスト型.NET APIへ明示的にアップロードできます。

本アプリは個人によるデータの可視化を目的としており、医療上の助言を提供するものではありません。詳細な機能、API仕様、端末テスト手順については、[英語版README](README.md)を基準とします。

## アーキテクチャ

```text
Health Connect
      |
      v
Android app (Kotlin, Jetpack Compose)
      |
      +-- Room / SQLite local cache
      +-- Charts, sleep insights, CSV / ZIP export
      +-- WorkManager sync and upload workers
      |
      v
ASP.NET Core 8 API (X-API-Key)
      |
      +-- SQLite uploaded-health tables
      +-- Static Plotly graph inspector
```

リポジトリは、Android Gradleプロジェクトの`AndroidClient/`、ASP.NET Core 8 APIとグラフ画面の`Server/`、サーバーテストの`Server.Tests/`、継続的に保守する文書の`docs/`で構成されています。

## 主な機能

- Health Connectの対応レコードをRoomデータベースへ読み込み、繰り返し確認・エクスポートできるローカルコピーを保持します。
- 心拍数、睡眠、歩数、距離、消費カロリー、体重、体脂肪率、血中酸素飽和度、血圧、体温、呼吸数、安静時心拍数を扱います。
- Jetpack Composeでダッシュボード、データカード、心拍タイムライン、安静時心拍数の推定、睡眠サマリー、raw recordの詳細を表示します。
- 心拍数互換CSV、全データCSV、データ種別ごとのCSV、ZIPパッケージを明示的に出力できます。
- 手動、smart、full、定期同期フローを備え、Androidで許可される範囲のバックグラウンド処理にはWorkManagerを使用します。
- `X-API-Key`で保護したセルフホスト型APIへ、正規化したrecords、values、aggregatesと任意のmedicine rowsをアップロードできます。
- 服薬スケジュール、服用記録、リマインダー、履歴を扱う任意の補助機能を含みます。

## プライバシーと検証範囲

- AndroidアプリはHealth Connectのレコードを読み取りますが、Health Connectへレコードを書き込みません。
- CSV／ZIPの生成とサーバーへのアップロードは、いずれもユーザーが明示的に操作した場合に実行されます。
- アップロードには接続先とAPI keyの設定が必要です。設定情報はAndroid Keystoreを利用したAES-GCMで暗号化されます。
- Android backupは無効化され、アプリ内のファイル、データベース、preferencesはcloud backupとdevice transferの対象外として明示的に除外されています。
- health export、local database、API key、device ID、端末検証用スクリーンショットはリポジトリへコミットしません。
- JVM unit testとserver testだけではリリース検証は完了しません。公開前には、対応emulatorと実機でのinstrumentation test、backup／restoreとdevice transfer、端末ロック中およびKeystore無効化後のsecure settings動作を確認する必要があります。

## ビルド入門

必要な環境：

- Java 17以降のJDK
- 実機またはemulatorを使用するためのAndroid SDK／Android Studio
- .NET 8 SDK

Androidアプリをビルド・テストします。

```powershell
cd AndroidClient

# Point JAVA_HOME at an installed Java 17+ JDK.
$env:JAVA_HOME = "<path-to-jdk-17>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

サーバーを起動します。

```powershell
cd Server
$keyBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($keyBytes)
$env:ApiKeys__0 = [Convert]::ToBase64String($keyBytes)
dotnet build HC_server.sln
dotnet run --project HC_server.csproj
```

上記コマンドは、現在のPowerShellセッションで使用するランダムな開発用キーを生成します。API keyはリポジトリに保存されません。アップロードAPI、smoke test、ローカル端末からの接続設定は[英語版README](README.md)を参照してください。

## ライセンス

本リポジトリのソースコードはGNU Affero General Public License v3.0 only（`AGPL-3.0-only`）で提供されています。詳細は[LICENSE](LICENSE)を参照してください。個別のライセンス表示を持つファイルには、その表示が適用されます。
