package xyz.qwexter.tat.utils

import app.cash.sqldelight.driver.native.inMemoryDriver
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.TestResult
import xyz.qwexter.AuthMode
import xyz.qwexter.configureDatabases
import xyz.qwexter.configureHTTP
import xyz.qwexter.configureRouting
import xyz.qwexter.configureSerialization
import xyz.qwexter.db
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.repository.FeedRepository
import xyz.qwexter.tat.repository.GroupsRepository
import xyz.qwexter.tat.repository.RecordsRepository
import xyz.qwexter.tat.repository.SpacesRepository
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
            val spacesRepository = SpacesRepository.create(db)
            configureRouting(
                tasksRepository = taskRepositoryFactory(),
                recordsRepository = recordsRepositoryFactory(),
                authMode = authMode,
                groupsRepository = GroupsRepository.create(db),
                feedRepository = FeedRepository.create(db),
                spacesRepository = spacesRepository,
                corsEnabled = false,
            )
        }
        client = createClient { install(ContentNegotiation) { json(Json { classDiscriminator = "kind" }) } }
        block()
    }

internal fun dbApp(
    authMode: AuthMode = AuthMode.NONE,
    block: suspend ApplicationTestBuilder.() -> Unit,
): TestResult =
    testApplication {
        application {
            configureHTTP()
            configureSerialization()
            configureDatabases(driver = inMemoryDriver(TatDatabase.Schema))
            configureRouting(
                tasksRepository = TasksRepository.create(db),
                recordsRepository = RecordsRepository.create(db),
                groupsRepository = GroupsRepository.create(db),
                feedRepository = FeedRepository.create(db),
                spacesRepository = SpacesRepository.create(db),
                authMode = authMode,
                corsEnabled = false,
            )
        }
        client = createClient { install(ContentNegotiation) { json(Json { classDiscriminator = "kind" }) } }
        block()
    }
