# Aurora Chat Tags — Fabric Minecraft 26.3

**Server-side only**: install on BisectHosting, not on player computers. Requires Minecraft 26.3, Fabric Loader 0.19.5+, Java 25 and Fabric API 0.161.0+26.3.

## Commands (only OPs / server console)

- `/auroratag set PlayerName Owner`
- `/auroratag set PlayerName Aurora`
- `/auroratag view PlayerName`
- `/auroratag remove PlayerName`

Player must be **online** to target them. Tags persist by UUID in the world folder `aurora-chat-tags.properties` (including after restarts). Tags accept 1-20 alphanumeric characters, underscore, or hyphen. This initial release uses a consistent light-blue tag color.

**Display note:** The tag is prepended to the *chat content*, after Minecraft's normal username prefix, e.g. `<PlayerName> [Owner] Hello`. This preserves vanilla signed chat handling; it is not a custom chat screen or nameplate mod. Other mods that replace chat rendering may conflict.

## Build

Java 25 + Gradle 9.7.1 required. In project root run `gradle build`; get the non-sources production `.jar` from `build/libs/`. GitHub Actions workflow included under `.github/workflows/build.yml` for cloud building.

**Build status:** source only; has NOT been compiled or run on a Minecraft 26.3 server. If compile errors arise they must be corrected prior to installing.

## BisectHosting installation

1. Back up your server, stop it.
2. Use Starbase Files > mods and upload the compiled `aurora-chat-tags-1.0.0.jar` (not the source ZIP).
3. Confirm the 26.3 Fabric API is installed; use Fabric and Java 25 server runtime.
4. Start server and OP your account in console with `op PlayerName`.
5. Join and run `/auroratag set PlayerName Owner` then send a chat message.

## Troubleshooting builds
In GitHub, upload the *contents* of the AuroraChatTags folder to the repository root, including `.github/workflows/build.yml` (the `.github` folder is hidden on some systems). Then open Actions > Build Aurora Chat Tags > Run workflow. If it fails, open the failed `gradle build` step and copy its error output. Dependency/build tools have been updated, but this mod's Java APIs have not been compile-verified.
