package xyz.qwexter

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TodoRoutingTest {

    private fun todoApp(block: suspend ApplicationTestBuilder.() -> Unit): TestResult =
        testApplication {
            application {
                configureHTTP()
                configureSerialization()
                configureDatabases()
                configureRouting()
            }
            block()
        }

    @Test
    fun `GET todos returns 200 on empty db`() = todoApp {
        val response = client.get("/todos")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST todo returns 201 and title`() = todoApp {
        val response = client.post("/todos") {
            setBody("Buy milk")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertContains(response.bodyAsText(), "Buy milk")
    }

    @Test
    fun `POST then GET returns inserted todo`() = todoApp {
        client.post("/todos") { setBody("Write tests") }

        val response = client.get("/todos")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Write tests")
    }
}
