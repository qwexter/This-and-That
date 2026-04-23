package xyz.qwexter.tat.utils

import io.ktor.server.application.Application
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import xyz.qwexter.db
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TasksRepository
import xyz.qwexter.tat.repository.toDb

val unimplementedTasksRepository: Application.() -> TasksRepository
    get() = {
        object : TasksRepository {
            override suspend fun allActiveTasks(): List<Task> {
                TODO("Not yet implemented")
            }

            override suspend fun createTask(
                name: String,
                description: String?,
                status: TaskStatus,
                priority: TaskPriority,
                deadline: LocalDateTime?,
            ): Task {
                TODO("Not yet implemented")
            }
        }
    }

fun createTasksRepositoryInMemoryList(initial: List<Task> = emptyList()): Application.() -> TasksRepository =
    { TasksRepository.buildInMemory(initial) }

fun createTasksRepositoryDb(
    dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    initial: List<Task> = emptyList(),
): Application.() -> TasksRepository {
    return {
        TasksRepository.create(this.db, dbDispatcher)
            .also {
                initial.forEach {
                    // use direct injection to DB to prevent autogeneration of ID and validation
                    db.tatDatabaseQueries.insertTask(
                        id = it.id.id,
                        name = it.name.name,
                        description = it.description,
                        priority = it.priority.toDb(),
                        status = it.status.toDb(),
                        deadline = it.deadline?.toInstant(TimeZone.UTC)?.toEpochMilliseconds(),
                        created_at = it.createdAt.toEpochMilliseconds(),
                        updated_at = it.updatedAt?.toEpochMilliseconds(),
                    )
                    if (it.deletedAt != null) {
                        db.tatDatabaseQueries.softDeleteTask(
                            task_deleted_at = it.deletedAt.toEpochMilliseconds(),
                            updated_at = it.updatedAt?.toEpochMilliseconds(),
                            id = it.id.id,
                        )
                    }
                }
            }
    }
}
