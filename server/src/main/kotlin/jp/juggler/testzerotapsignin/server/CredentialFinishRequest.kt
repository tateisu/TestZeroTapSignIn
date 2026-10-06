package jp.juggler.testzerotapsignin.server

// POST /restore/registration/finish のリクエストボディ
data class CredentialFinishRequest(
    val requestId: String,
    val responseJson: String
)
