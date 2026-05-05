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
import xyz.qwexter.Repositories
import xyz.qwexter.configureDatabases
import xyz.qwexter.configureHTTP
import xyz.qwexter.configureRouting
import xyz.qwexter.configureSerialization
import xyz.qwexter.db
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.repository.FeedRepository
import xyz.qwexter.tat.repository.GroupsRepository
import xyz.qwexter.tat.repository.InvitesRepository
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
            configureRouting(
                repositories = Repositories(
                    tasks = taskRepositoryFactory(),
                    records = recordsRepositoryFactory(),
                    groups = GroupsRepository.create(db),
                    feed = FeedRepository.create(db),
                    spaces = SpacesRepository.create(db),
                    invites = InvitesRepository.create(db),
                ),
                authMode = authMode,
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
                repositories = Repositories(
                    tasks = TasksRepository.create(db),
                    records = RecordsRepository.create(db),
                    groups = GroupsRepository.create(db),
                    feed = FeedRepository.create(db),
                    spaces = SpacesRepository.create(db),
                    invites = InvitesRepository.create(db),
                ),
                authMode = authMode,
                corsEnabled = false,
            )
        }
        client = createClient { install(ContentNegotiation) { json(Json { classDiscriminator = "kind" }) } }
        block()
    }
