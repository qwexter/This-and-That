package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import xyz.qwexter.tat.models.Record
import xyz.qwexter.tat.models.RecordId
import xyz.qwexter.tat.models.RecordTitle
import xyz.qwexter.tat.routing.ActiveRecord
import xyz.qwexter.tat.routing.AddRecord
import xyz.qwexter.tat.routing.UpdateRecord
import xyz.qwexter.tat.utils.todoApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecordsRoutingTest {

    private val now = Clock.System.now()

    private val testRecords = listOf(
        Record(
            id = RecordId("record-1"),
            ownerId = "dev-user",
            title = RecordTitle("Shopping list"),
            content = "Milk, eggs, bread",
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
            groupId = null,
        ),
        Record(
            id = RecordId("record-2"),
            ownerId = "dev-user",
            title = RecordTitle("Meeting notes"),
            content = null,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
            groupId = null,
        ),
        Record(
            id = RecordId("record-3"),
            ownerId = "dev-user",
            title = RecordTitle("Deleted note"),
            content = "Should not appear",
            createdAt = now,
            updatedAt = now,
            deletedAt = now,
            groupId = null,
        ),
    )

    @Test
    fun `GET records returns empty list when repository is empty`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.get("records")
        val actual = response.body<List<ActiveRecord>>()
        assertTrue(actual.isEmpty())
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
    }

    @Test
    fun `GET records returns only active records`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.get("records")
        val actual = response.body<List<ActiveRecord>>()
        assertEquals(2, actual.size)
        assertTrue(actual.none { it.id == "record-3" })
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET record by id returns stored record`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.get("records/record-1")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ActiveRecord>()
        assertEquals("record-1", body.id)
        assertEquals("Shopping list", body.title)
        assertEquals("Milk, eggs, bread", body.content)
    }

    @Test
    fun `GET record by id returns 404 for unknown id`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        assertEquals(HttpStatusCode.NotFound, client.get("records/unknown-id").status)
    }

    @Test
    fun `GET record by id returns 404 for deleted record`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        assertEquals(HttpStatusCode.NotFound, client.get("records/record-3").status)
    }

    @Test
    fun `POST records returns Created and valid model`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "My note", content = "Some content"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ActiveRecord>()
        assertEquals("My note", body.title)
        assertEquals("Some content", body.content)
        assertNotNull(body.id)
    }

    @Test
    fun `POST records creates record without content`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "Title only"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ActiveRecord>()
        assertEquals("Title only", body.title)
        assertEquals(null, body.content)
    }

    @Test
    fun `POST records trims title whitespace`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "  Trimmed  "))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("Trimmed", response.body<ActiveRecord>().title)
    }

    @Test
    fun `POST records returns 400 when title is blank`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            setBody(AddRecord(title = "   "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST records returns 400 when title is empty`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            setBody(AddRecord(title = ""))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST records returns 400 when title exceeds 200 characters`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            setBody(AddRecord(title = "a".repeat(201)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST records returns 400 when content exceeds 5000 characters`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            setBody(AddRecord(title = "Valid title", content = "a".repeat(5001)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST records accepts content at exactly 5000 characters`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "Valid title", content = "a".repeat(5000)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST records returns 400 on malformed JSON`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val response = client.post("records") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST records appears in GET list`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(emptyList()) },
    ) {
        val created = client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "New note"))
        }.body<ActiveRecord>()

        val list = client.get("records").body<List<ActiveRecord>>()
        assertTrue(created in list)
    }

    @Test
    fun `PATCH record updates title`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(UpdateRecord(title = "Updated title"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Updated title", response.body<ActiveRecord>().title)
    }

    @Test
    fun `PATCH record updates content`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(UpdateRecord(content = "New content"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("New content", response.body<ActiveRecord>().content)
    }

    @Test
    fun `PATCH record trims title whitespace`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(UpdateRecord(title = "  Trimmed  "))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Trimmed", response.body<ActiveRecord>().title)
    }

    @Test
    fun `PATCH record returns 400 when title is blank`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRecord(title = "   "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH record returns 400 when title exceeds 200 characters`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRecord(title = "a".repeat(201)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH record returns 400 when content exceeds 5000 characters`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRecord(content = "a".repeat(5001)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH record returns 404 for unknown id`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/unknown-id") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRecord(title = "X"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH record returns 400 on malformed JSON`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        val response = client.patch("records/record-1") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE record returns 204 and record no longer in GET list`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        assertEquals(HttpStatusCode.NoContent, client.delete("records/record-1").status)
        val list = client.get("records").body<List<ActiveRecord>>()
        assertTrue(list.none { it.id == "record-1" })
    }

    @Test
    fun `DELETE record returns 404 for unknown id`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        assertEquals(HttpStatusCode.NotFound, client.delete("records/unknown-id").status)
    }

    @Test
    fun `DELETE record returns 404 for already deleted record`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        assertEquals(HttpStatusCode.NotFound, client.delete("records/record-3").status)
    }

    @Test
    fun `DELETE record GET by id returns 404 after deletion`() = todoApp(
        recordsRepositoryFactory = { xyz.qwexter.tat.repository.RecordsRepository.buildInMemory(testRecords) },
    ) {
        client.delete("records/record-1")
        assertEquals(HttpStatusCode.NotFound, client.get("records/record-1").status)
    }
}
