package jp.juggler.testzerotapsignin.server

import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.ByteArray as WebAuthnByteArray

const val DEMO_USER = "demo"

fun demoUserIdentity(): UserIdentity = UserIdentity.builder()
    .name(DEMO_USER)
    .displayName("Demo User")
    .id(WebAuthnByteArray("testzerotapsignin-demo-user".toByteArray()))
    .build()
