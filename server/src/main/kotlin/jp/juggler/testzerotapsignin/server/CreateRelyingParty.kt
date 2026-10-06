package jp.juggler.testzerotapsignin.server

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity
import java.util.Base64

fun createRelyingParty(
    repository: CredentialRepository,
    rpId: String = System.getenv("WEBAUTHN_RP_ID") ?: "localhost",
    appFingerPrint: String = System.getenv("ANDROID_SHA256_CERT_FINGERPRINT")
        ?: error("missing env ANDROID_SHA256_CERT_FINGERPRINT"),
    configuredOrigins: String? = System.getenv("WEBAUTHN_ORIGINS")
        ?.takeIf { it.isNotBlank() },
): RelyingParty = RelyingParty.builder()
    .identity(
        RelyingPartyIdentity.builder()
            .id(rpId)
            .name("Test Zero Tap Sign In")
            .build()
    )
    .credentialRepository(repository)
    .origins(
        (
                configuredOrigins?.split(',')
                    ?: listOfNotNull(
                        "https://$rpId",
                        appFingerPrint
                            .replace(":", "")
                            .chunked(2)
                            .map { it.toInt(16).toByte() }
                            .toByteArray()
                            .let {
                                "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(it)
                            }
                    )
                )
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
    )
    .allowUntrustedAttestation(true)
    .build()
