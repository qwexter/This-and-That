package xyz.qwexter.tat.utils

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.TestResult
import xyz.qwexter.configureDatabases
import xyz.qwexter.configureHTTP
import xyz.qwexter.configureRouting
import xyz.qwexter.configureSerialization
import xyz.qwexter.tat.repository.TasksRepository

internal fun todoApp(
    tasksRepository: TasksRepository = TasksRepository.buildInMemory(emptyList()),
    block: suspend ApplicationTestBuilder.() -> Unit,
): TestResult =
    testApplication {
        application {
            configureHTTP()
            configureSerialization()
            configureDatabases()
            configureRouting(tasksRepository)
        }
        block()
    }