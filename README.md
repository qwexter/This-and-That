# This and That - TaT

TaT is a solution for small task and record management, like a todo-list with a few extras:
- [ ] todo items with severity and optional deadline
- [ ] plain text records
- [ ] grouping of tasks and records
- [ ] ability to share access to a specific group (with equal rights to edit)
- [ ] PWA application to be able to work offline with cached data

# Tech stuff

## Tech Stack

| Library       | Version | Purpose                        |
|---------------|---------|--------------------------------|
| Kotlin        | 2.3.0   | Language / Native compiler     |
| Ktor          | 3.4.2   | HTTP server (CIO engine)       |
| SQLDelight    | 2.3.2   | SQL database (native SQLite)   |


## Ktor Features 

| Name                                                                           | Description                                                                        |
|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Default Headers](https://start.ktor.io/p/default-headers)                    | Adds a default set of headers to HTTP responses                                    |
| [Routing](https://start.ktor.io/p/routing)                                    | Provides a structured routing DSL                                                  |
| [Resources](https://start.ktor.io/p/resources)                                | Provides type-safe routing                                                         |
| [Status Pages](https://start.ktor.io/p/status-pages)                          | Provides exception handling for routes                                             |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)            | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization)        | Handles JSON serialization using kotlinx.serialization library                     |
| [SQLDelight](https://sqldelight.github.io/sqldelight/2.3.2/multiplatform_sqlite/) | Type-safe SQL with native SQLite driver, no JVM required                       |

## Building & Running

The project compiles to a native binary via Kotlin/Native (no JVM required at runtime).
The target is selected automatically based on the host OS.

| Task                          | Description                                      |
|-------------------------------|--------------------------------------------------|
| `./gradlew build`             | Build everything and run tests                   |
| `./gradlew runDebugExecutableNative` | Run the server (debug binary)             |
| `./gradlew nativeBinaries`    | Compile native binaries only                     |
| `./gradlew linkReleaseExecutableNative` | Build optimized release binary          |

The compiled binary is output to:
```
build/bin/native/debugExecutable/todo.kexe       # debug
build/bin/native/releaseExecutable/todo.kexe     # release
```

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```