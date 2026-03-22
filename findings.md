# Findings & Decisions

## Requirements
- User wants the Claude relay smoke test actually run through locally.
- Smoke test should use the real Claude upstream key provided by the user.
- Minimize disruption to the currently running local environment.

## Research Findings
- Source code contains relay classes in `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`, `backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`, and `backend/src/main/java/com/firstapi/backend/service/RelayService.java`.
- The currently running process on port `8080` is using `backend/target/backend-0.0.1-SNAPSHOT.jar`, but that jar does not contain relay classes when inspected.
- Current MySQL schema in the active environment does not have `relay_records`, which is present in `backend/src/main/resources/schema.sql`.
- Current `8080` instance is therefore not a valid target for relay smoke verification.
- `mvn -version` shows Maven is currently running on Java 8 (`1.8.0_482`), which is too old for `org.springframework.boot:spring-boot-maven-plugin:4.0.3`.
- A newer JDK is installed locally at `C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot`.
- No JDK 25 installation was found locally, while `backend/pom.xml` sets `<java.version>25</java.version>`.
- After deleting stale `.class` files and recompiling under JDK 21, an isolated backend instance on port `8082` started successfully and created the `relay_records` table.
- Real non-stream Claude relay traffic reached Anthropic and was rejected with `authentication_error` / `invalid x-api-key`.
- Real stream Claude relay traffic returned `502 upstream_error` locally after the same upstream credential failure path.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Start a separate backend instance on another port | Avoid disturbing the existing local services while still testing the latest code |
| Use backend APIs to create temporary platform keys and accounts | Reuse existing encryption and persistence logic instead of manual DB writes |
| Clean temporary records immediately after tests | Real upstream credentials should not remain in local storage |
| Use the isolated `8082` runtime as the smoke-test evidence source | It is the only runtime confirmed to include relay classes and current schema |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Current runtime and current source are out of sync | Rebuild and run a fresh instance from latest source |
| Previous smoke attempt timed out | Narrowed failure boundary before applying any fix |
| Default Maven runtime is too old for Spring Boot 4 build plugins | Use the installed JDK 21 only for the build and isolated runtime commands |
| Project build target is Java 25 but local machine lacks JDK 25 | Try a local CLI-only override to Java 21 without modifying repository files |
| Existing compiled classes were built for Java 25 (`major version 69`) | Removed stale `.class` files and recompiled under JDK 21 |
| Supplied Claude key failed official upstream authentication | Smoke test completed, but result is a verified upstream auth failure rather than a successful relay |

## Resources
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/controller/RelayController.java`
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- `D:/FirstApi/backend/src/main/resources/schema.sql`
- `D:/FirstApi/backend/DEPLOY.md`
- `C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot`

## Visual/Browser Findings
- None yet.
