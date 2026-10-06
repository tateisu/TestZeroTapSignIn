## Playストアの「Zero-tap sign-in restoration」要件
- https://support.google.com/googleplay/android-developer/answer/17492799
- **2027年4月から必須** 未対応の場合、Google Play上の**可視性や公開能力に影響する可能性**があります。[Google サポート](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en-en)
- 対象は現在 **スマートフォンとタブレット**。ゲームは現時点では対象外です。常時非公開アプリや企業向け端末管理アプリも対象外で、金融・医療など厳格な規制要件があるアプリは免除申請できる場合があります。[Google サポート](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en-en)
- ログイン機能があるアプリは、ユーザーが旧Android端末から新端末へ移行し、端末間転送またはクラウドバックアップでデータを復元した場合、**ユーザー操作なしでログイン状態を復元できること**が求められます。任意ログインのアプリも対象です。[Google サポート](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en-en)
- ログアウト済み・ゲスト状態だったユーザーは、移行後も未認証状態で起動すればよく、復元対象は「旧端末でアクティブだったログイン状態」です。複数アカウントの場合は、現在または最後に使用していた1アカウントを復元対象にします。[Google サポート](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en-en)
- Googleは適合判定について、**restore keyの取得成功によってsign-in restorationの成功を判断する**と説明しています。[Google サポート](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en-en)

## TestZeroTapSignIn
- Restore Credentials API を使うAndroidアプリとアプリサーバのペア

## アプリサーバ実装
Note: このサンプルはデモユーザー、セッション、登録済み公開鍵、チャレンジをすべてメモリに保持するため、サーバ再起動で消えます。
Note: `/demo/sign-in` は認証なしでログインできる試験用 endpoint です。
本番では実際のアカウント認証、永続ストレージ、HTTPS、期限付きセッション、レート制限に置き換えてください。

### 技術スタック
- Kotlin, ktor, kotest
- Yubico WebAuthn ライブラリ com.yubico:webauthn-server-core

### 環境変数

| 変数                            | デフォルト値                                 | 説明                                                                                                                                               |
|---------------------------------|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| PORT                            | 8080                                         | Ktor サーバーの待受ポート。                                                                                                                        |
| ANDROID_SHA256_CERT_FINGERPRINT | (必須)                                       | Android アプリの署名証明書の SHA-256 fingerprint。コロン区切りで指定します。WebAuthn の Android origin 設定と `assetlinks.json` の応答に使います。 |
| WEBAUTHN_RP_ID                  | localhost                                    | WebAuthn の RP ID。通常はアプリの RP ドメインを指定します。                                                                                        |
| WEBAUTHN_ORIGINS                | `https://<WEBAUTHN_RP_ID>` と Android origin | WebAuthn 検証で許可する origin のカンマ区切り一覧。指定すると既定の originを置き換えます。                                                         |

### 実行例
```
# 起動
./gradlew :server:run

# 単体テスト
./gradlew :server:test
```

### エンドポイント

| エンドポイント | 認証 | 説明 |
| --- | --- | --- |
| `GET /health` | 不要 | 稼働確認用です。`OK` を返します。 |
| `GET /.well-known/assetlinks.json` | 不要 | Digital Asset Links を返します。`ANDROID_SHA256_CERT_FINGERPRINT` が未設定の場合は `404` です。 |
| `POST /demo/sign-in` | 不要 | 固定のデモユーザーとしてログインし、`token` と `user` を返します。試験専用 endpoint です。 |
| `POST /restore/registration/options` | Bearer token | Restore Credential 登録用の `requestId` と WebAuthn `requestJson` を返します。登録済みアカウントのセッショントークンが必要です。 |
| `POST /restore/registration/finish` | Bearer token | 登録応答を検証して公開鍵を保存します。JSON body は `requestId` と `responseJson` です。成功時はユーザー名と登録成功フラグを返し、検証エラー時は `400` を返します。 |
| `POST /restore/authentication/options` | 不要 | 未ログイン端末が Restore Credential を取得するための `requestId` と WebAuthn `requestJson` を返します。 |
| `POST /restore/authentication/finish` | 不要 | Restore Credential の認証応答を検証します。JSON body は `requestId` と `responseJson` です。成功時は新しい `token` と `user` を返し、認証失敗時は `401` を返します。 |

`requestId` は options と対応する一時的な WebAuthn request を識別します。サンプルではセッション、登録済み公開鍵、未完了の request をメモリ上に保持するため、サーバー再起動で失われます。


## Notes
動作確認にはアプリと RP ドメインの関連付けが必要です。し、そのドメインで `/.well-known/assetlinks.json` が HTTPS 経由で取得できるようにします。

Android Studio の debug keystore を使う場合、fingerprint は `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey` で確認できます。

アプリは同じサーバ URL に接続するよう `./gradlew :app:installDebug -PserverUrl=https://<RPドメイン>` としてビルドします。

デバイスに応じて Google Play services と画面ロック/バックアップ設定も必要です。
