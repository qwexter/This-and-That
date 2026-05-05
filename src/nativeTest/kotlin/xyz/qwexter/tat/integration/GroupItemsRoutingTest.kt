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
import xyz.qwexter.tat.routing.ActiveRecord
import xyz.qwexter.tat.routing.ActiveSpace
import xyz.qwexter.tat.routing.ActiveTask
import xyz.qwexter.tat.routing.AddGroup
import xyz.qwexter.tat.routing.AddGroupItem
import xyz.qwexter.tat.routing.AddGroupItemsRequest
import xyz.qwexter.tat.routing.AddGroupItemsResponse
import xyz.qwexter.tat.routing.AddRecord
import xyz.qwexter.tat.routing.AddSpace
import xyz.qwexter.tat.routing.AddSpaceMember
import xyz.qwexter.tat.routing.AddTask
import xyz.qwexter.tat.routing.ApiTaskPriority
import xyz.qwexter.tat.routing.ApiTaskStatus
import xyz.qwexter.tat.routing.GroupItemResponse
import xyz.qwexter.tat.utils.dbApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupItemsRoutingTest {

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createGroup(title: String = "G"): String =
        client.post("groups") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroup(title = title))
        }.body<ActiveGroup>().id

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createTask(name: String = "T"): String =
        client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = name, description = null, status = null, priority = null, deadline = null))
        }.body<ActiveTask>().id

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createRecord(title: String = "R"): String =
        client.post("records") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddRecord(title = title))
        }.body<ActiveRecord>().id

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.addItems(
        groupId: String,
        vararg items: AddGroupItem,
    ) = client.post("groups/$groupId/items") {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
        setBody(AddGroupItemsRequest(items = items.toList()))
    }

    // --- positive ---

    @Test
    fun `empty items list returns 200 with empty result`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid)
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.body<AddGroupItemsResponse>().items.isEmpty())
    }

    @Test
    fun `create new task in group`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewTask(name = "New task"))
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = resp.body<AddGroupItemsResponse>().items
        assertEquals(1, items.size)
        val task = assertIs<GroupItemResponse.TaskResponse>(items.single())
        assertEquals("New task", task.name)
        assertEquals(gid, task.groupId)
        assertEquals(ApiTaskStatus.Todo, task.status)
        assertEquals(ApiTaskPriority.Low, task.priority)
    }

    @Test
    fun `create new record in group`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewRecord(title = "New record", content = "body"))
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = resp.body<AddGroupItemsResponse>().items
        val record = assertIs<GroupItemResponse.RecordResponse>(items.single())
        assertEquals("New record", record.title)
        assertEquals("body", record.content)
        assertEquals(gid, record.groupId)
    }

    @Test
    fun `bind existing task to group`() = dbApp {
        val gid = createGroup()
        val tid = createTask("Existing task")
        val resp = addItems(gid, AddGroupItem.ExistingTask(id = tid))
        assertEquals(HttpStatusCode.OK, resp.status)
        val task = assertIs<GroupItemResponse.TaskResponse>(resp.body<AddGroupItemsResponse>().items.single())
        assertEquals(tid, task.id)
        assertEquals(gid, task.groupId)
    }

    @Test
    fun `bind existing record to group`() = dbApp {
        val gid = createGroup()
        val rid = createRecord("Existing record")
        val resp = addItems(gid, AddGroupItem.ExistingRecord(id = rid))
        assertEquals(HttpStatusCode.OK, resp.status)
        val record = assertIs<GroupItemResponse.RecordResponse>(resp.body<AddGroupItemsResponse>().items.single())
        assertEquals(rid, record.id)
        assertEquals(gid, record.groupId)
    }

    @Test
    fun `mixed new and existing items in one call`() = dbApp {
        val gid = createGroup()
        val tid = createTask("Solo task")
        val resp = addItems(
            gid,
            AddGroupItem.NewTask(name = "Fresh task"),
            AddGroupItem.NewRecord(title = "Fresh record"),
            AddGroupItem.ExistingTask(id = tid),
        )
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = resp.body<AddGroupItemsResponse>().items
        assertEquals(3, items.size)
        assertTrue(items.all { it is GroupItemResponse.TaskResponse || it is GroupItemResponse.RecordResponse })
        assertTrue(items.all {
            when (it) {
                is GroupItemResponse.TaskResponse -> it.groupId == gid
                is GroupItemResponse.RecordResponse -> it.groupId == gid
            }
        })
    }

    @Test
    fun `binding task already in same group is idempotent`() = dbApp {
        val gid = createGroup()
        val tid = createTask()
        addItems(gid, AddGroupItem.ExistingTask(id = tid))
        val resp = addItems(gid, AddGroupItem.ExistingTask(id = tid))
        assertEquals(HttpStatusCode.OK, resp.status)
        val task = assertIs<GroupItemResponse.TaskResponse>(resp.body<AddGroupItemsResponse>().items.single())
        assertEquals(gid, task.groupId)
    }

    @Test
    fun `new task name is trimmed`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewTask(name = "  Trimmed  "))
        assertEquals(HttpStatusCode.OK, resp.status)
        val task = assertIs<GroupItemResponse.TaskResponse>(resp.body<AddGroupItemsResponse>().items.single())
        assertEquals("Trimmed", task.name)
    }

    @Test
    fun `new record title is trimmed`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewRecord(title = "  Trimmed  "))
        assertEquals(HttpStatusCode.OK, resp.status)
        val record = assertIs<GroupItemResponse.RecordResponse>(resp.body<AddGroupItemsResponse>().items.single())
        assertEquals("Trimmed", record.title)
    }

    // --- negative ---

    @Test
    fun `group not found returns 404`() = dbApp {
        val resp = addItems("no-such-group", AddGroupItem.NewTask(name = "T"))
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `existing task not found returns 400`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.ExistingTask(id = "no-such-task"))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `existing record not found returns 400`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.ExistingRecord(id = "no-such-record"))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `task already in other group returns 400 and nothing is written`() = dbApp {
        val g1 = createGroup("Group 1")
        val g2 = createGroup("Group 2")
        val tid = createTask("Task")
        addItems(g1, AddGroupItem.ExistingTask(id = tid))

        val resp = addItems(g2, AddGroupItem.ExistingTask(id = tid))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `record already in other group returns 400`() = dbApp {
        val g1 = createGroup("Group 1")
        val g2 = createGroup("Group 2")
        val rid = createRecord("Record")
        addItems(g1, AddGroupItem.ExistingRecord(id = rid))

        val resp = addItems(g2, AddGroupItem.ExistingRecord(id = rid))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `failure on one item prevents all writes`() = dbApp {
        val gid = createGroup()
        val resp = addItems(
            gid,
            AddGroupItem.NewTask(name = "Good task"),
            AddGroupItem.ExistingTask(id = "does-not-exist"),
        )
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        // group should have no children — transaction rolled back
        val feedPage = client.get("feed").body<xyz.qwexter.tat.routing.ApiFeedPage>()
        val group = feedPage.items
            .filterIsInstance<xyz.qwexter.tat.routing.ApiFeedEntry.ApiGroupEntry>()
            .first { it.id == gid }
        assertTrue(group.children.isEmpty())
    }

    @Test
    fun `new task with blank name returns 400`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewTask(name = "   "))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `new record with blank title returns 400`() = dbApp {
        val gid = createGroup()
        val resp = addItems(gid, AddGroupItem.NewRecord(title = ""))
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `malformed JSON returns 400`() = dbApp {
        val gid = createGroup()
        val resp = client.post("groups/$gid/items") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // --- auth / shared-space access ---

    @Test
    fun `member of shared space can add items to group in that space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Team"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Shared Group", spaceId = spaceId))
        }.body<ActiveGroup>().id

        val resp = client.post("groups/$groupId/items") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroupItemsRequest(items = listOf(AddGroupItem.NewTask(name = "Bob Task"))))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = resp.body<AddGroupItemsResponse>().items
        assertEquals(1, items.size)
        assertIs<GroupItemResponse.TaskResponse>(items.single()).also {
            assertEquals("Bob Task", it.name)
            assertEquals(groupId, it.groupId)
        }
    }

    @Test
    fun `non-member cannot add items to group in another user's private space`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Private Group"))
        }.body<ActiveGroup>().id

        val resp = client.post("groups/$groupId/items") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroupItemsRequest(items = listOf(AddGroupItem.NewTask(name = "Intruder Task"))))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `owner can always add items to own groups`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Group"))
        }.body<ActiveGroup>().id

        val resp = client.post("groups/$groupId/items") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddGroupItemsRequest(items = listOf(AddGroupItem.NewTask(name = "Alice Task"))))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }
}
