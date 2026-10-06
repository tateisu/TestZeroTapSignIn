package jp.juggler.testzerotapsignin.server

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserVerificationRequirement
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ApiServer(
    private val repository: DemoCredentialRepository,
    private val webAuthn: RelyingParty,
) {
    /**
     * 認証トークンとユーザ(デモ実装ではユーザ名文字列)のマップ
     * 本来ならDBなどに永続化する必要がある
     */
    private val sessions = ConcurrentHashMap<String, String>()

    /**
     * requestIdとcredentials登録リクエストのマップ
     * 本来ならDBなどに永続化する必要がある
     */
    private val registrationRequests =
        ConcurrentHashMap<String, PublicKeyCredentialCreationOptions>()

    /**
     * requestIdとcredentials復元リクエストのマップ
     * 本来ならDBなどに永続化する必要がある
     */
    private val authenticationRequests =
        ConcurrentHashMap<String, AssertionRequest>()

    /**
     * トークン生成用の乱数ソース
     */
    private val secureRandom = SecureRandom()

    /**
     * 認証ヘッダに付与する認証トークンを作成する
     */
    private fun createSessionToken(): String =
        ByteArray(32).also(secureRandom::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /**
     * 認証トークンにマッチするユーザを返す
     */
    fun userForToken(token: String?): String? = token?.let { sessions[it] }

    /**
     * 認証ヘッダにマッチするユーザを返す
     * デモ実装では認証チェックの代わりに使われている
     */
    fun ApplicationCall.getUser(): String? =
        userForToken(request.headers["Authorization"]?.removePrefix("Bearer "))

    /**
     * ログイン処理
     * デモ実装なのでユーザ名もパスワードもない
     */
    fun demoSignIn(): Map<String, String> {
        val token = createSessionToken()
        sessions[token] = DEMO_USER
        return mapOf("token" to token, "user" to DEMO_USER)
    }

    /**
     * credentials登録の開始
     */
    fun startRegistration(): CredentialOptionsResponse {
        val options = webAuthn.startRegistration(
            StartRegistrationOptions.builder()
                .user(demoUserIdentity())
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.DISCOURAGED)
                        .build()
                )
                .build()
        )
        val requestId = UUID.randomUUID().toString()
        registrationRequests[requestId] = options
        return CredentialOptionsResponse(requestId, options.toCredentialsCreateJson())
    }

    /**
     * credentials登録の終了
     */
    fun finishRegistration(requestId: String, responseJson: String) {
        val request = registrationRequests.remove(requestId)
            ?: error("Unknown or expired registration request")
        val credential = PublicKeyCredential.parseRegistrationResponseJson(responseJson)
        val result = webAuthn.finishRegistration(
            FinishRegistrationOptions.builder().request(request).response(credential).build()
        )
        repository.add(
            RegisteredCredential.builder()
                .credentialId(result.keyId.id)
                .userHandle(demoUserIdentity().id)
                .publicKeyCose(result.publicKeyCose)
                .signatureCount(result.signatureCount)
                .build()
        )
    }

    /**
     * credentials復元の開始
     */
    fun startAuthentication(): CredentialOptionsResponse {
        val request = webAuthn.startAssertion(StartAssertionOptions.builder().build())
        val requestId = UUID.randomUUID().toString()
        authenticationRequests[requestId] = request
        return CredentialOptionsResponse(requestId, request.toCredentialsGetJson())
    }

    /**
     * credentials復元の終了
     */
    fun finishAuthentication(requestId: String, responseJson: String): Map<String, String>? {
        // 復元要求リクエストをpop
        val request = authenticationRequests.remove(requestId)
            ?: error("Unknown or expired authentication request")

        val credential = PublicKeyCredential.parseAssertionResponseJson(responseJson)
        val result = webAuthn.finishAssertion(
            FinishAssertionOptions.builder().request(request).response(credential).build()
        )
        if (!result.isSuccess) return null
        repository.updateSignatureCount(
            result.credential.credentialId,
            result.signatureCount,
        )
        // 認証セッションを開始して認証トークンを返す
        val token = createSessionToken()
        sessions[token] = result.username
        return mapOf("token" to token, "user" to result.username)
    }

    /**
     * KtorのRouting にAPI定義を追加
     */
    fun route(application: Application) = application.routing {
        // サーバの生存確認
        get("/health") { call.respondText("OK") }

        // credential 機能の提供を端末に教えるための静的JSON
        get("/.well-known/assetlinks.json") {
            val fingerprint = System.getenv("ANDROID_SHA256_CERT_FINGERPRINT")
                ?: return@get call.respondText(
                    "ANDROID_SHA256_CERT_FINGERPRINT is not configured",
                    status = io.ktor.http.HttpStatusCode.NotFound
                )
            call.respond(
                listOf(
                    mapOf(
                        "relation" to listOf("delegate_permission/common.get_login_creds"),
                        "target" to mapOf(
                            "namespace" to "android_app",
                            "package_name" to "jp.juggler.testzerotapsignin",
                            "sha256_cert_fingerprints" to listOf(fingerprint)
                        )
                    )
                )
            )
        }
        // ログインAPI。デモ用なのでユーザ名は固定
        post("/demo/sign-in") {
            call.respond(demoSignIn())
        }

        // credentialの保存開始
        post("/restore/registration/options") {
            call.getUser() ?: return@post call.respondText(
                "Unauthorized",
                status = io.ktor.http.HttpStatusCode.Unauthorized
            )
            call.respond(startRegistration())
        }

        // credentialの保存完了をアプリサーバに覚える
        post("/restore/registration/finish") {
            call.getUser() ?: return@post call.respondText(
                "Unauthorized",
                status = io.ktor.http.HttpStatusCode.Unauthorized
            )
            val body = call.receive<CredentialFinishRequest>()
            try {
                finishRegistration(body.requestId, body.responseJson)
                call.respond(mapOf("user" to DEMO_USER, "restoreCredentialRegistered" to true))
            } catch (e: Exception) {
                call.respondText(
                    e.message ?: "Credential registration failed",
                    status = io.ktor.http.HttpStatusCode.BadRequest
                )
            }
        }
        // credentialsを使った情報復元の開始
        post("/restore/authentication/options") {
            call.respond(startAuthentication())
        }
        // credentialsを使った情報復元の終了
        post("/restore/authentication/finish") {
            val body = call.receive<CredentialFinishRequest>()
            try {
                call.respond(
                    finishAuthentication(
                        requestId = body.requestId,
                        responseJson = body.responseJson
                    ) ?: return@post call.respondText(
                        "Authentication failed",
                        status = io.ktor.http.HttpStatusCode.Unauthorized
                    )
                )
            } catch (e: Exception) {
                call.respondText(
                    e.message ?: "Authentication failed",
                    status = io.ktor.http.HttpStatusCode.Unauthorized
                )
            }
        }
    }
}
