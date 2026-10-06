package jp.juggler.testzerotapsignin.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ServerTest : StringSpec({
    "health endpoint reports that the server is ready" {
        testApplication {
            application { module()
            }

            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK"
        }
    }
})
