package jp.juggler.testzerotapsignin

import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Restore Credentials の登録・認証と、デモ用サーバーセッションの管理を行うリポジトリ。
 *
 * サーバーとの通信、Credential Manager の操作、ログイン状態の端末保存をまとめて扱います。
 *
 * @param context アプリケーションコンテキストとセッション保存先の取得に使うコンテキスト。
 * @param credentialManager Restore Credential の作成・取得・削除に使う Credential Manager。
 * @param httpClient サーバー API との HTTP 通信に使うクライアント。
 * @param dispatcherProvider 非同期処理で使用する CoroutineDispatcher の提供元。
 */
class RestoreCredentialsRepository(
    context: Context,
    private val credentialManager: CredentialManager,
    private val httpClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
) {
    companion object {
        const val PREF_USER_NAME = "userName"
        const val PREF_TOKEN = "token"

        /**
         * Credential Manager で Restore Credential を作成します。
         * E2EE が利用できない場合はクラウドバックアップを無効にして再試行します。
         *
         * @param requestJson サーバーが生成した Credential 作成要求。
         * @return サーバーへ返す Credential 作成レスポンス JSON。
         */
        private suspend fun CredentialManager.createCredentialRetry(
            context: Context, requestJson: String
        ): String {
            val response = try {
                createCredential(
                    context,
                    CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = true)
                )
            } catch (_: E2eeUnavailableException) {
                createCredential(
                    context,
                    CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = false)
                )
            }
            return (response as? CreateRestoreCredentialResponse
                ?: error("Restore Credential が返されませんでした")).responseJson
        }
    }

    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(
        "demo-session",
        Context.MODE_PRIVATE,
    )

    /**
     * 指定したサーバー API に JSON POST を行います。
     *
     * @param path サーバー URL に連結する API パス。
     * @param token 指定された場合に Bearer 認証ヘッダーへ設定するトークン。
     * @param body POST する JSON 文字列。
     * @return サーバー応答を解析した JSON オブジェクト。
     * @throws IllegalStateException HTTP 応答が成功ではない場合。
     */
    private suspend fun post(path: String, token: String? = null, body: String): JSONObject =
        withContext(dispatcherProvider.io) {
            val requestBody = body.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )
            val requestBuilder = Request.Builder()
                .url(BuildConfig.SERVER_URL.trimEnd('/') + path)
                .post(requestBody)
            token?.let { requestBuilder.header("Authorization", "Bearer $it") }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseText = response.body.string()
                check(response.isSuccessful) { "HTTP ${response.code}: $responseText" }
                JSONObject(responseText)
            }
        }

    /** 保存済みのサーバーセッショントークンがあるかを返します。 */
    fun hasSession(): Boolean = preferences.contains(PREF_TOKEN)

    /**
     * デモユーザーとしてサーバーにログインし、セッショントークンを端末に保存します。
     *
     * @return サーバーが発行したセッショントークン。
     * @throws Exception 通信に失敗した場合、またはサーバーがエラーを返した場合。
     */
    suspend fun signIn() {
        val result = post("/demo/sign-in", body = "{}")
        preferences.edit {
            putString(PREF_TOKEN, result.getString("token"))
            putString(PREF_USER_NAME, result.getString("user"))
        }
    }

    /**
     * サーバーから登録オプションを取得し、Restore Credential を作成して登録を完了します。
     *
     * 事前に [signIn] でデモユーザーとしてログインしている必要があります。
     *
     * @throws IllegalStateException セッションが保存されていない場合。
     * @throws Exception Credential Manager またはサーバー API の処理に失敗した場合。
     */
    suspend fun registerDemoRestoreCredential() {
        val token = preferences.getString("token", null)
            ?: error("ログインしていません")
        // アプリサーバから optionsを取得
        val options = post("/restore/registration/options", token, "{}")
        // CredentialManagerにrestore key を保存する
        val responseJson = credentialManager.createCredentialRetry(
            context = appContext,
            requestJson = options.getString("requestJson"),
        )
        // 一連の情報をアプリサーバ側に記録する
        post(
            "/restore/registration/finish",
            token,
            JSONObject().apply {
                put("requestId", options.getString("requestId"))
                put("responseJson", responseJson)
            }.toString()
        )
    }

    /**
     * Restore Credential を取得してサーバー認証を完了し、
     * 発行されたセッションを保存します。
     *
     * @return サーバー認証されたユーザー名。
     * @throws Exception Credential Manager またはサーバー API の処理に失敗した場合。
     */
    suspend fun signInWithRestoreCredential() {
        // アプリサーバから optionsを取得
        val options = post("/restore/authentication/options", body = "{}")
        // CredentialManager で復元できるか調べる
        val result = credentialManager.getCredential(
            appContext,
            GetCredentialRequest(
                listOf(
                    GetRestoreCredentialOption(options.getString("requestJson"))
                )
            )
        )
        val responseJson = (result.credential as? RestoreCredential
            ?: error("Restore Credential が返されませんでした"))
            .authenticationResponseJson
        // 復元した情報をアプリサーバに送る
        val response = post(
            "/restore/authentication/finish",
            body = JSONObject().apply {
                put("requestId", options.getString("requestId"))
                put("responseJson", responseJson)
            }.toString(),
        )
        // アプリサーバ応答に新しい認証トークンが入っている
        preferences.edit {
            putString(PREF_TOKEN, response.getString("token"))
            putString(PREF_USER_NAME, response.getString("user"))
        }
    }

    /**
     * Restore Credential と保存済みのサーバーセッションを削除します。
     * Credential Manager の削除に失敗した場合、セッションの消去は行われません。
     */
    suspend fun signOut() {
        credentialManager.clearCredentialState(
            ClearCredentialStateRequest(
                ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL
            )
        )
        preferences.edit { clear() }
    }
}
