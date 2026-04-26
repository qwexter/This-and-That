package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import xyz.qwexter.AuthMode
import xyz.qwexter.tat.routing.ActiveGroup
import xyz.qwexter.tat.routing.ActiveSpace
import xyz.qwexter.tat.routing.AddGroup
import xyz.qwexter.tat.routing.AddRecord
import xyz.qwexter.tat.routing.AddSpace
import xyz.qwexter.tat.routing.AddSpaceMember
import xyz.qwexter.tat.routing.AddTask
import xyz.qwexter.tat.routing.ApiFeedEntry
import xyz.qwexter.tat.routing.ApiFeedPage
import xyz.qwexter.tat.utils.dbApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedRoutingTest {

    @Test
    fun `GET feed returns empty page when nothing created`() = dbApp {
        val response = client.get("feed")
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<ApiFeedPage>()
        assertTrue(page.items.isEmpty())
        assertEquals(0L, page.total)
    }

    @Test
    fun `GET feed returns solo task`() = dbApp {
        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Feed task", description = null, status = null, priority = null, deadline = null))
        }
        val page = client.get("feed").body<ApiFeedPage>()
        assertEquals(1L, page.total)
        val entry = page.items.single()
        assertTrue(entry is ApiFeedEntry.ApiTaskEntry)
        assertEquals("Feed task", entry.name)
    }

    @Test
    fun `GET feed returns solo record`() = dbApp {
        client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "Feed record"))
        }
        val page = client.get("feed").body<ApiFeedPage>()
        assertEquals(1L, page.total)
        val entry = page.items.single()
        assertTrue(entry is ApiFeedEntry.ApiRecordEntry)
        assertEquals("Feed record", entry.title)
    }

    @Test
    fun `GET feed returns both tasks and records`() = dbApp {
        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Task A", description = null, status = null, priority = null, deadline = null))
        }
        client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "Record B"))
        }
        val page = client.get("feed").body<ApiFeedPage>()
        assertEquals(2L, page.total)
        assertTrue(page.items.any { it is ApiFeedEntry.ApiTaskEntry })
        assertTrue(page.items.any { it is ApiFeedEntry.ApiRecordEntry })
    }

    @Test
    fun `GET feed includes group with children`() = dbApp {
        val groupId = client.post("groups") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group"))
        }.body<ActiveGroup>().id

        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Grouped task", description = null, status = null, priority = null, deadline = null, groupId = groupId))
        }
        client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = "Grouped record", groupId = groupId))
        }

        val page = client.get("feed").body<ApiFeedPage>()
        assertEquals(1L, page.total)
        val group = page.items.single()
        assertTrue(group is ApiFeedEntry.ApiGroupEntry)
        assertEquals("My Group", group.title)
        assertEquals(2, group.children.size)
    }

    @Test
    fun `GET feed excludes grouped tasks and records from top level`() = dbApp {
        val groupId = client.post("groups") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroup(title = "Group"))
        }.body<ActiveGroup>().id

        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Grouped", description = null, status = null, priority = null, deadline = null, groupId = groupId))
        }
        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Solo", description = null, status = null, priority = null, deadline = null))
        }

        val page = client.get("feed").body<ApiFeedPage>()
        // group + solo task
        assertEquals(2L, page.total)
        val kinds = page.items.map { it::class.simpleName }
        assertTrue(kinds.contains("ApiGroupEntry"))
        assertTrue(kinds.contains("ApiTaskEntry"))
        assertTrue(page.items.filterIsInstance<ApiFeedEntry.ApiTaskEntry>().none { it.name == "Grouped" })
    }

    @Test
    fun `GET feed pagination limit and offset`() = dbApp {
        repeat(5) { i ->
            client.post("tasks") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(AddTask(name = "Task $i", description = null, status = null, priority = null, deadline = null))
            }
        }
        val page1 = client.get("feed?limit=2&offset=0").body<ApiFeedPage>()
        assertEquals(5L, page1.total)
        assertEquals(2, page1.items.size)

        val page2 = client.get("feed?limit=2&offset=2").body<ApiFeedPage>()
        assertEquals(5L, page2.total)
        assertEquals(2, page2.items.size)

        val page3 = client.get("feed?limit=2&offset=4").body<ApiFeedPage>()
        assertEquals(5L, page3.total)
        assertEquals(1, page3.items.size)
    }

    @Test
    fun `GET feed default limit is 20`() = dbApp {
        val page = client.get("feed").body<ApiFeedPage>()
        assertEquals(20L, page.limit)
    }

    @Test
    fun `GET feed clamps limit to max 100`() = dbApp {
        val page = client.get("feed?limit=999").body<ApiFeedPage>()
        assertEquals(100L, page.limit)
    }

    // --- shared-space access ---

    @Test
    fun `member sees shared-space groups in feed`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Team"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        client.post("groups") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Shared Group", spaceId = spaceId))
        }

        val page = client.get("feed") { header("X-User-Id", "bob") }.body<ApiFeedPage>()
        val groups = page.items.filterIsInstance<ApiFeedEntry.ApiGroupEntry>()
        assertTrue(groups.any { it.title == "Shared Group" })
    }

    @Test
    fun `member does NOT see other user's private-space groups in feed`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Private Group"))
        }

        val page = client.get("feed") { header("X-User-Id", "bob") }.body<ApiFeedPage>()
        val groups = page.items.filterIsInstance<ApiFeedEntry.ApiGroupEntry>()
        assertTrue(groups.none { it.title == "Alice Private Group" })
    }

    @Test
    fun `solo tasks and records belong only to their owner in feed`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("tasks") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "Alice Task", description = null, status = null, priority = null, deadline = null))
        }
        client.post("records") {
            header("X-User-Id", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddRecord(title = "Alice Record"))
        }

        val page = client.get("feed") { header("X-User-Id", "bob") }.body<ApiFeedPage>()
        assertTrue(page.items.isEmpty())
    }
}
