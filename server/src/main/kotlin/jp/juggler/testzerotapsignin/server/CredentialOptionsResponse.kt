package jp.juggler.testzerotapsignin.server

// POST /restore/registration/options の応答
data class CredentialOptionsResponse(
    val requestId: String,
    val requestJson: String,
)
