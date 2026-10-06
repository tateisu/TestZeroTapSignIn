plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass = "jp.juggler.testzerotapsignin.server.MainKt"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.yubico.webauthn)
    implementation(libs.koin.ktor)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest.runner.junit5)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
