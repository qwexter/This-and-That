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
import xyz.qwexter.tat.routing.ActiveSpaceMember
import xyz.qwexter.tat.routing.AddGroup
import xyz.qwexter.tat.routing.AddSpace
import xyz.qwexter.tat.routing.AddSpaceMember
import xyz.qwexter.tat.routing.UpdateGroup
import xyz.qwexter.tat.routing.UpdateSpace
import xyz.qwexter.tat.utils.dbApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacesRoutingTest {

    // --- CRUD ---

    @Test
    fun `POST spaces creates space and GET returns it`() = dbApp(authMode = AuthMode.HEADER) {
        val created = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "My Space"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<ActiveSpace>()

        assertEquals("alice", created.ownerId)
        assertEquals("My Space", created.title)

        val list = client.get("spaces") {
            header("X-Auth-Request-User", "alice")
        }.body<List<ActiveSpace>>()

        assertEquals(1, list.size)
        assertEquals(created.id, list.single().id)
    }

    @Test
    fun `GET spaces returns only caller-accessible spaces`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Alice Space"))
        }
        client.post("spaces") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Bob Space"))
        }

        val aliceSpaces = client.get("spaces") { header("X-Auth-Request-User", "alice") }.body<List<ActiveSpace>>()
        assertEquals(1, aliceSpaces.size)
        assertEquals("Alice Space", aliceSpaces.single().title)
    }

    @Test
    fun `GET space by id returns 404 for another user's space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Secret"))
        }.body<ActiveSpace>().id

        val response = client.get("spaces/$spaceId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH space updates title`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Old Title"))
        }.body<ActiveSpace>().id

        val updated = client.patch("spaces/$spaceId") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(UpdateSpace(title = "New Title"))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<ActiveSpace>()

        assertEquals("New Title", updated.title)
    }

    @Test
    fun `PATCH space returns 404 when space belongs to another user`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Alice Space"))
        }.body<ActiveSpace>().id

        val response = client.patch("spaces/$spaceId") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            setBody(UpdateSpace(title = "Hijacked"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE space returns 204 and space no longer appears in list`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Temp"))
        }.body<ActiveSpace>().id

        assertEquals(HttpStatusCode.NoContent, client.delete("spaces/$spaceId") {
            header("X-Auth-Request-User", "alice")
        }.status)

        val list = client.get("spaces") { header("X-Auth-Request-User", "alice") }.body<List<ActiveSpace>>()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `DELETE space returns 404 when space belongs to another user`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Alice Space"))
        }.body<ActiveSpace>().id

        val response = client.delete("spaces/$spaceId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST spaces returns 400 when title is blank`() = dbApp(authMode = AuthMode.HEADER) {
        val response = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "   "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET spaces returns 401 when header missing`() = dbApp(authMode = AuthMode.HEADER) {
        assertEquals(HttpStatusCode.Unauthorized, client.get("spaces").status)
    }

    // --- Member management ---

    @Test
    fun `POST members adds member and GET members lists them`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }

        val members = client.get("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
        }.body<List<ActiveSpaceMember>>()

        assertEquals(1, members.size)
        assertEquals("bob", members.single().userId)
    }

    @Test
    fun `GET members returns 404 for non-owner`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        val response = client.get("spaces/$spaceId/members") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST members returns 404 for non-owner`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        val response = client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "charlie"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST members returns 400 when adding yourself`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        val response = client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "alice"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE member removes member`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        assertEquals(HttpStatusCode.NoContent, client.delete("spaces/$spaceId/members/bob") {
            header("X-Auth-Request-User", "alice")
        }.status)

        val members = client.get("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
        }.body<List<ActiveSpaceMember>>()
        assertTrue(members.isEmpty())
    }

    @Test
    fun `DELETE member returns 404 for non-existent member`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Shared"))
        }.body<ActiveSpace>().id

        val response = client.delete("spaces/$spaceId/members/nobody") { header("X-Auth-Request-User", "alice") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Shared access ---

    @Test
    fun `member can GET space by id`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Collab"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        val response = client.get("spaces/$spaceId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Collab", response.body<ActiveSpace>().title)
    }

    @Test
    fun `member sees space in GET spaces list`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Collab"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        val bobSpaces = client.get("spaces") { header("X-Auth-Request-User", "bob") }.body<List<ActiveSpace>>()
        assertEquals(1, bobSpaces.size)
        assertEquals(spaceId, bobSpaces.single().id)
    }

    @Test
    fun `member cannot PATCH space`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Collab"))
        }.body<ActiveSpace>().id

        client.post("spaces/$spaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }

        val response = client.patch("spaces/$spaceId") {
            header("X-Auth-Request-User", "bob")
            contentType(ContentType.Application.Json)
            setBody(UpdateSpace(title = "Hijacked"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Group / space integration ---

    @Test
    fun `group assigned to space is accessible by member`() = dbApp(authMode = AuthMode.HEADER) {
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
            setBody(AddGroup(title = "Sprint 1", spaceId = spaceId))
        }.body<ActiveGroup>().id

        val response = client.get("groups/$groupId") { header("X-Auth-Request-User", "bob") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Sprint 1", response.body<ActiveGroup>().title)
    }

    @Test
    fun `group can be moved into space via PATCH`() = dbApp(authMode = AuthMode.HEADER) {
        val spaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Team"))
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
    fun `clearSpace moves group back to private space`() = dbApp(authMode = AuthMode.HEADER) {
        val sharedSpaceId = client.post("spaces") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpace(title = "Team"))
        }.body<ActiveSpace>().id

        val groupId = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group", spaceId = sharedSpaceId))
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

    // --- Private space ---

    @Test
    fun `creating a group auto-creates private space for caller`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "First Group"))
        }

        val spaces = client.get("spaces") { header("X-Auth-Request-User", "alice") }.body<List<ActiveSpace>>()
        assertEquals(1, spaces.size)
        val private = spaces.single()
        assertEquals(true, private.isPrivate)
        assertEquals("Private", private.title)
        assertEquals("alice", private.ownerId)
    }

    @Test
    fun `private space created only once for same user`() = dbApp(authMode = AuthMode.HEADER) {
        repeat(3) {
            client.post("groups") {
                header("X-Auth-Request-User", "alice")
                contentType(ContentType.Application.Json)
                setBody(AddGroup(title = "Group $it"))
            }
        }

        val spaces = client.get("spaces") { header("X-Auth-Request-User", "alice") }.body<List<ActiveSpace>>()
        assertEquals(1, spaces.filter { it.isPrivate }.size)
    }

    @Test
    fun `group created without spaceId goes into private space`() = dbApp(authMode = AuthMode.HEADER) {
        val group = client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "My Group"))
        }.body<ActiveGroup>()

        val spaces = client.get("spaces") { header("X-Auth-Request-User", "alice") }.body<List<ActiveSpace>>()
        val privateSpaceId = spaces.single { it.isPrivate }.id
        assertEquals(privateSpaceId, group.spaceId)
    }

    @Test
    fun `DELETE private space returns 400`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "First Group"))
        }

        val privateSpaceId = client.get("spaces") { header("X-Auth-Request-User", "alice") }
            .body<List<ActiveSpace>>().single { it.isPrivate }.id

        val response = client.delete("spaces/$privateSpaceId") { header("X-Auth-Request-User", "alice") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST members to private space returns 400`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "First Group"))
        }

        val privateSpaceId = client.get("spaces") { header("X-Auth-Request-User", "alice") }
            .body<List<ActiveSpace>>().single { it.isPrivate }.id

        val response = client.post("spaces/$privateSpaceId/members") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddSpaceMember(userId = "bob"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `private space not visible to other users`() = dbApp(authMode = AuthMode.HEADER) {
        client.post("groups") {
            header("X-Auth-Request-User", "alice")
            contentType(ContentType.Application.Json)
            setBody(AddGroup(title = "Alice Group"))
        }

        val bobSpaces = client.get("spaces") { header("X-Auth-Request-User", "bob") }.body<List<ActiveSpace>>()
        assertTrue(bobSpaces.isEmpty())
    }
}
