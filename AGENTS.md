# Repository rules

- Use a standalone Java 25 JDK and the Maven Wrapper with the five-module reactor. Keep Maven compiler release, Enforcer, CI, Docker, VS Code, and documentation on Java 25.
- Run `./mvnw -B -ntp clean verify` after meaningful Java changes. Maven Wrapper is the only supported Maven prerequisite.
- Preserve dependencies: application → domain; server → application/domain/protocol; client → protocol. Domain has no infrastructure dependencies; protocol has no game rules or domain entities.
- Keep packages under `lt.ktu.paskutinisatsiskaitymas`. Use `GameCharacter`, not `Character`.
- Domain/application must not import Swing, AWT, networking, WebSocket or Jackson. Preserve Enforcer and Checkstyle checks; do not evade them with reflection or fully qualified names.
- Server owns authoritative rules. WebSocket callbacks handle transport/connection state and translate gameplay messages into application commands queued for a single-owner game loop; they must not mutate the game world.
- Keep wire DTOs separate from entities. Use explicit JSON type names and test round trips and invalid input.
- Use Swing/Java2D and the standard Java WebSocket client. Dispatch UI updates on the EDT; never block it with network I/O.
- Do not add Spring, JavaFX, Lombok, DI frameworks, ECS/game frameworks or a database. Add design patterns only for concrete requirements.
- Keep shared VS Code files portable: no personal settings, absolute paths, or configured JDK installation paths.
- Dockerize only the server. Preserve direct runnable JARs, configurable addresses/ports, non-root/headless execution, graceful cleanup and ARM64/AMD64 portability.
- Preserve the implemented game: up to four players, character/stat selection, movement, jumping, gravity, collision, combat, NPC behavior, items, stress, respawn, and authoritative snapshots. Combat and Training are implemented arena recipes; each server process runs one arena selected at startup. Lobby, rooms, and runtime arena switching are not implemented. Do not add these, scoring, persistence, authentication, match results, or other new gameplay without an explicit task.
- Keep documentation aligned. Tests must stop all servers, clients and workers. Do not commit, push or open a PR unless explicitly asked.
