package xyz.qwexter.tat.utils

import app.cash.sqldelight.driver.native.inMemoryDriver
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.TestResult
import xyz.qwexter.AuthMode
import xyz.qwexter.configureDatabases
import xyz.qwexter.configureHTTP
import xyz.qwexter.configureRouting
import xyz.qwexter.configureSerialization
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.repository.RecordsRepository
import xyz.qwexter.tat.repository.TasksRepository

internal fun todoApp(
    taskRepositoryFactory: Application.() -> TasksRepository = createTasksRepositoryInMemoryList(emptyList()),
    recordsRepositoryFactory: Application.() -> RecordsRepository = { RecordsRepository.buildInMemory(emptyList()) },
    authMode: AuthMode = AuthMode.NONE,
    block: suspend ApplicationTestBuilder.() -> Unit,
): TestResult =
    testApplication {
        application {
            configureHTTP()
            configureSerialization()
            configureDatabases(driver = inMemoryDriver(TatDatabase.Schema))
            configureRouting(
                tasksRepository = taskRepositoryFactory(),
                recordsRepository = recordsRepositoryFactory(),
                authMode = authMode,
            )
        }
        client = createClient { install(ContentNegotiation) { json() } }
        block()
    }
