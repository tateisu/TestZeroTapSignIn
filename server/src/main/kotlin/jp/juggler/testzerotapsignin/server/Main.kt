package jp.juggler.testzerotapsignin.server

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.dsl.module as koinModule

fun Application.module() {
    // bodyのデータ型変換にjacksonを使う
    install(ContentNegotiation) { jackson() }

    // koin DI供給コード。ApiServerとその依存関係
    install(Koin) {
        modules(
            koinModule {
                single { DemoCredentialRepository() }
                single { createRelyingParty(get()) }
                single { ApiServer(get(), get()) }
            }
        )
    }

    // DI要求
    val apiServer: ApiServer by inject()

    // ktorのルーティング追加
    apiServer.route(this)
}

fun main() {
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        module = Application::module,
    ).start(wait = true)
}
