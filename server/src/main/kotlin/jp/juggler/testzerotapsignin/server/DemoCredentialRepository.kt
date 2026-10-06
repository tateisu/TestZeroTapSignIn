package jp.juggler.testzerotapsignin.server

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import com.yubico.webauthn.data.ByteArray as WebAuthnByteArray

class DemoCredentialRepository : CredentialRepository {
    companion object {
        private val demoUserIdentityId: WebAuthnByteArray = demoUserIdentity().id
    }

    // credentialId.base64Url と RegisteredCredential のマップ。
    // Note: 本来はDBなどに永続化する必要がある
    private val credentials = ConcurrentHashMap<String, RegisteredCredential>()

    fun add(credential: RegisteredCredential) {
        credentials[credential.credentialId.base64Url] = credential
    }

    fun updateSignatureCount(credentialId: WebAuthnByteArray, count: Long) {
        val key = credentialId.base64Url
        credentials.computeIfPresent(key) { _, previous ->
            previous.toBuilder().signatureCount(count).build()
        }
    }

    override fun getCredentialIdsForUsername(
        username: String
    ) = credentials.values
        .filter { username == DEMO_USER && it.userHandle == demoUserIdentityId }
        .map { PublicKeyCredentialDescriptor.builder().id(it.credentialId).build() }
        .toSet()

    override fun getUserHandleForUsername(
        username: String,
    ): Optional<WebAuthnByteArray> = when (username) {
        DEMO_USER -> Optional.of(demoUserIdentityId)
        else -> Optional.empty()
    }

    override fun getUsernameForUserHandle(
        userHandle: WebAuthnByteArray,
    ): Optional<String> = when (userHandle) {
        demoUserIdentityId -> Optional.of(DEMO_USER)
        else -> Optional.empty()
    }

    override fun lookup(
        credentialId: WebAuthnByteArray,
        userHandle: WebAuthnByteArray,
    ): Optional<RegisteredCredential> =
        credentials[credentialId.base64Url]
            ?.takeIf { it.userHandle == userHandle }
            ?.let { Optional.of(it) }
            ?: Optional.empty()

    override fun lookupAll(
        credentialId: WebAuthnByteArray,
    ) = credentials[credentialId.base64Url]?.let { setOf(it) } ?: emptySet()
}
