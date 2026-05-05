package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import xyz.qwexter.AuthMode
import xyz.qwexter.tat.routing.ActiveGroup
import xyz.qwexter.tat.routing.ActiveSpace
import xyz.qwexter.tat.routing.AddGroup
import xyz.qwexter.tat.routing.AddSpace
import xyz.qwexter.tat.routing.AddSpaceMember
import xyz.qwexter.tat.routing.UpdateGroup
import xyz.qwexter.tat.utils.dbApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupsRoutingTest {

    // --- GET /groups ---

    @Test
    fun `GET groups returns own groups`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Group"))
        }

        val groups = client.get("groups") { header("X-Auth-Request-User", "alice") }.body<List<ActiveGroup>>()
        assertEquals(1, groups.size)
        assertEquals("Alice Group", groups.single().title)
    }

    @Test
    fun `GET groups does not return other user's private groups`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Private"))
        }

        val groups = client.get("groups") { header("X-Auth-Request-User", "bob") }.body<List<ActiveGroup>>()
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `GET groups includes shared-space groups for member`() = dbApp(authMode = AuthMode.HEADER) {
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

        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Shared Group", spaceId = spaceId))
        }

        val groups = client.get("groups") { header("X-Auth-Request-User", "bob") }.body<List<ActiveGroup>>()
        assertEquals(1, groups.size)
        assertEquals("Shared Group", groups.single().title)
    }

    @Test
    fun `GET groups returns 401 when header missing`() = dbApp(authMode = AuthMode.HEADER) {
        assertEquals(HttpStatusCode.Unauthorized, client.get("groups").status)
    }

    // --- GET /groups/{id} ---

    @Test
    fun `GET group by id returns 404 for unknown id`() = dbApp(authMode = AuthMode.HEADER) {
        val response = client.get("groups/no-such-id") { header("X-Auth-Request-User", "alice") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET group by id returns 404 for another user's private group`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Private"))
        }.body<ActiveGroup>().id

        val response = client.get("groups/$groupId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET group by id returns 200 for shared-space group where caller is member`() = dbApp(authMode = AuthMode.HEADER) {
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
            setBody(AddGroup(title = "Shared", spaceId = spaceId))
        }.body<ActiveGroup>().id

        val response = client.get("groups/$groupId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Shared", response.body<ActiveGroup>().title)
    }

    // --- POST /groups ---

    @Test
    fun `POST groups without spaceId goes to private space`() = dbApp(authMode = AuthMode.HEADER) {
        val group = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<ActiveGroup>()

        val privateSpaceId = client.get("spaces") { header("X-Auth-Request-User", "alice") }
            .body<List<ActiveSpace>>().single { it.isPrivate }.id
        assertEquals(privateSpaceId, group.spaceId)
    }

    @Test
    fun `POST groups with explicit spaceId goes to that space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Project"))
        }.body<ActiveSpace>().id

        val group = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Sprint 1", spaceId = spaceId))
        }.body<ActiveGroup>()

        assertEquals(spaceId, group.spaceId)
    }

    @Test
    fun `POST groups with blank title returns 400`() = dbApp(authMode = AuthMode.HEADER) {
        val response = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "  "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST groups returns 401 when header missing`() = dbApp(authMode = AuthMode.HEADER) {
        val response = client.post("groups") {
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "No Auth"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- PATCH /groups/{id} ---

    @Test
    fun `PATCH groups renames group`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Old Name"))
        }.body<ActiveGroup>().id

        val updated = client.patch("groups/$groupId") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(UpdateGroup(title = "New Name"))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<ActiveGroup>()

        assertEquals("New Name", updated.title)
    }

    @Test
    fun `PATCH groups assigns group to space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Work"))
        }.body<ActiveSpace>().id

        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group"))
        }.body<ActiveGroup>().id

        val updated = client.patch("groups/$groupId") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(UpdateGroup(spaceId = spaceId))
        }.body<ActiveGroup>()

        assertEquals(spaceId, updated.spaceId)
    }

    @Test
    fun `PATCH groups clearSpace moves group back to private space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Work"))
        }.body<ActiveSpace>().id

        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group", spaceId = spaceId))
        }.body<ActiveGroup>().id

        val updated = client.patch("groups/$groupId") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(UpdateGroup(clearSpace = true))
        }.body<ActiveGroup>()

        val privateSpaceId = client.get("spaces") { header("X-Auth-Request-User", "alice") }
            .body<List<ActiveSpace>>().single { it.isPrivate }.id
        assertEquals(privateSpaceId, updated.spaceId)
    }

    @Test
    fun `PATCH groups returns 404 for another user's group`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Group"))
        }.body<ActiveGroup>().id

        val response = client.patch("groups/$groupId") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            setBody(UpdateGroup(title = "Hijacked"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- DELETE /groups/{id} ---

    @Test
    fun `DELETE groups returns 204 and group no longer appears in list`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Temp"))
        }.body<ActiveGroup>().id

        assertEquals(HttpStatusCode.NoContent, client.delete("groups/$groupId") {
            header("X-Auth-Request-User", "alice")
        }.status)

        val groups = client.get("groups") { header("X-Auth-Request-User", "alice") }.body<List<ActiveGroup>>()
        assertTrue(groups.none { it.id == groupId })
    }

    @Test
    fun `DELETE groups returns 404 for another user's group`() = dbApp(authMode = AuthMode.HEADER) {
        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Group"))
        }.body<ActiveGroup>().id

        val response = client.delete("groups/$groupId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE groups returns 404 for unknown id`() = dbApp(authMode = AuthMode.HEADER) {
        val response = client.delete("groups/no-such-id") { header("X-Auth-Request-User", "alice") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
