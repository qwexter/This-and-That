package xyz.qwexter.tat

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.TestResult
import xyz.qwexter.configureDatabases
import xyz.qwexter.configureHTTP
import xyz.qwexter.configureRouting
import xyz.qwexter.configureSerialization

class TasksRoutingTest {

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


}