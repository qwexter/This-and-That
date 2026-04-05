# This and That - TaT

TaT is a solution for small task and record management, like a todo-list with a few extras:
- [ ] todo items with severity and optional deadline
- [ ] plain text records
- [ ] grouping of tasks and records
- [ ] ability to share access to a specific group (with equal rights to edit)
- [ ] PWA application to be able to work offline with cached data

# Tech stuff

## Tech Stack

| Library          | Version | Purpose                             |
|------------------|---------|-------------------------------------|
| Kotlin           | 2.3.0   | Language / Native compiler (LLVM)   |
| Ktor             | 3.4.2   | HTTP server (CIO engine, native)    |
| SQLDelight       | 2.3.2   | Type-safe SQL, native SQLite driver |
| kotlinx-datetime | 0.6.2   | Multiplatform date/time             |
| Detekt           | 1.23.8  | Static analysis + formatting        |

## Ktor Features

| Name                                                                               | Description                                                                        |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Default Headers](https://start.ktor.io/p/default-headers)                        | Adds a default set of headers to HTTP responses                                    |
| [Routing](https://start.ktor.io/p/routing)                                        | Provides a structured routing DSL                                                  |
| [Resources](https://start.ktor.io/p/resources)                                    | Provides type-safe routing                                                         |
| [Status Pages](https://start.ktor.io/p/status-pages)                              | Provides exception handling for routes                                             |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)                | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization)            | Handles JSON serialization using kotlinx.serialization library                     |
| [SQLDelight](https://sqldelight.github.io/sqldelight/2.3.2/multiplatform_sqlite/) | Type-safe SQL with native SQLite driver, no JVM required                           |

## Building & Running

The project compiles to a native binary via Kotlin/Native (LLVM backend). No JVM required at runtime.
The target platform is selected automatically based on the host OS at build time.

| Task                                    | Description                      |
|-----------------------------------------|----------------------------------|
| `./gradlew linkDebugExecutableNative`   | Compile debug binary             |
| `./gradlew linkReleaseExecutableNative` | Compile optimized release binary |
| `./gradlew runDebugExecutableNative`    | Compile and run debug binary     |
| `./gradlew allTests`                    | Run tests for current platform   |
| `./gradlew detekt`                      | Run linter                       |

The compiled binary is output to:
```
build/bin/native/debugExecutable/todo.exe        # Windows
build/bin/native/debugExecutable/todo.kexe       # Linux / macOS
build/bin/native/releaseExecutable/todo.exe      # Windows release
build/bin/native/releaseExecutable/todo.kexe     # Linux / macOS release
```

### Windows prerequisite

On Windows, SQLite is bundled as a static library in `libs/windows/libsqlite3.a` (compiled with
Kotlin/Native's own clang — no MSYS2 or external toolchain required at build time).

## CI

GitHub Actions runs on every push and pull request to `main`:

| Job    | Runner          | What it does                       |
|--------|-----------------|------------------------------------|
| `lint` | `ubuntu-latest` | Runs `detekt` (style + coroutines) |
| `test` | `ubuntu-latest` | Runs `nativeTest` (linuxX64)       |

Tests run only after lint passes. The Kotlin/Native toolchain (`~/.konan`) is cached between runs.

Detekt config: `config/detekt/detekt.yml`. Active rule sets: `formatting`, `style`, `complexity`, `coroutines`, `naming`.

## API

The server listens on `http://0.0.0.0:8080`.

### Todos

| Method | Path   | Body       | Description         |
|--------|--------|------------|---------------------|
| GET    | /todos | —          | List all todo items |
| POST   | /todos | plain text | Create a new todo   |

**Examples:**
```bash
# Create todos
curl -X POST http://localhost:8080/todos -d "Buy milk"
curl -X POST http://localhost:8080/todos -d "Write code"

# List all
curl http://localhost:8080/todos
# #1 Buy milk done=0
# #2 Write code done=0
```

## Database

SQLite database file `tat.db` is created in the working directory on first run.
Schema is managed by SQLDelight — `.sq` files live in `src/commonMain/sqldelight/`.
