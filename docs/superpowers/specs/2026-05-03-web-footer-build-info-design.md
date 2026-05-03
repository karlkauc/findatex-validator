# Web Footer: Build Info (Version, Commit, Build Time)

**Date:** 2026-05-03
**Module:** `web-app` (no changes to `core` or `javafx-app`)

## Goal

Show the running build's identity in the web app's footer so operators can confirm
which version of the container is live without shelling in.

Footer goes from:

```
FinDatEx Validator — Source & Desktop-Build: com.findatex/findatex-validator
```

to:

```
FinDatEx Validator v1.0.0 · a1b2c3d-dirty · built 2026-05-03
— Source & Desktop-Build: com.findatex/findatex-validator
```

The `-dirty` suffix appears only when the Maven build was run against a working
tree with uncommitted changes. The date is the Maven build timestamp (when the
container was packaged), not the commit time.

## Out of scope

- JavaFX desktop app: the footer addition is web-only. Desktop version is
  already visible via the title bar / About dialog (separate concern).
- `core` module: no changes — build identity is a deployment artifact concern,
  not a validation-engine concern.
- Health/status endpoints: this is intentionally a tiny purpose-built endpoint,
  not a full Quarkus `quarkus-info` integration. We can revisit if more
  diagnostic data is needed later.

## Backend (`web-app`)

### Maven plugin

Add `io.github.git-commit-id:git-commit-id-maven-plugin` to `web-app/pom.xml`,
phase `initialize`, goal `revision`. Configuration:

- `generateGitPropertiesFile=true` → writes `target/classes/git.properties`.
- `dotGitDirectory=${project.basedir}/../.git` (web-app is a child module).
- `failOnNoGitDirectory=false` → packaging in a Docker stage that copies a
  shallow checkout still works; the resource just becomes empty.
- Properties to capture: `git.commit.id.abbrev` (7 chars), `git.dirty`,
  `git.build.time` (ISO-8601 UTC).

### Endpoint

`GET /api/build-info` — `BuildInfoResource` in
`web-app/src/main/java/com/findatex/validator/web/api/`.

Response DTO `BuildInfo` (record in `web-app/src/main/java/com/findatex/validator/web/dto/`):

```json
{
  "version": "1.0.0",
  "commit": "a1b2c3d",
  "dirty": true,
  "buildTime": "2026-05-03T14:32:00Z"
}
```

Field sources:

| Field       | Source                                                                  |
| ----------- | ----------------------------------------------------------------------- |
| `version`   | `@ConfigProperty(name = "quarkus.application.version")` (auto-populated by Quarkus from the POM `${project.version}`) |
| `commit`    | `git.commit.id.abbrev` from classpath `/git.properties`                 |
| `dirty`     | `git.dirty` from classpath `/git.properties` parsed as boolean          |
| `buildTime` | `git.build.time` from classpath `/git.properties`                       |

Loading strategy: `@ApplicationScoped` bean reads `/git.properties` once via
`@PostConstruct` into a `java.util.Properties`, returns the cached `BuildInfo`
on every request. If the resource is missing or unreadable, the bean falls back
to empty strings for the git fields and `dirty=false`; the version field still
returns the Maven version.

Caching: response is effectively static for the lifetime of the process. No
HTTP cache headers needed — react-query keeps it client-side with
`staleTime: Infinity`.

### Test

`BuildInfoResourceTest` (RestAssured, mirrors existing
`web-app/src/test/java/com/findatex/validator/web/api/*Test.java` style):

- Asserts the response contains the four fields with non-null types matching
  the schema above.
- Does **not** assert on specific values (commit hash will vary across
  developer machines and CI). It checks `version` matches the regex `^\d+\.\d+\.\d+(-SNAPSHOT)?$`
  and that `commit`, `buildTime` are strings (possibly empty in test classpath
  if the plugin doesn't run before surefire — accept that).

## Frontend (`web-app/src/main/frontend`)

### API client

Add `fetchBuildInfo()` to the existing API client module (same file as
`fetchTemplates()` etc.). Returns a typed `BuildInfo` interface:

```ts
export interface BuildInfo {
  version: string;
  commit: string;
  dirty: boolean;
  buildTime: string; // ISO-8601
}
```

### Hook + render

In `App.tsx`:

- Add `useQuery({ queryKey: ['build-info'], queryFn: fetchBuildInfo, staleTime: Infinity, retry: false })`.
- Replace the footer block at `App.tsx:243-248` with a renderer that:
  - While `isLoading` or `isError`: show only the existing static line
    (`FinDatEx Validator — Source & Desktop-Build: …`). No flicker.
  - On success: prepend a build-identity line, e.g.:

    ```tsx
    <div className="mx-auto max-w-[1600px] px-6 py-4 text-xs text-slate-500 lg:px-8">
      <div>
        FinDatEx Validator v{info.version} · <span className="font-mono">{info.commit}{info.dirty ? '-dirty' : ''}</span> · built {formatBuildDate(info.buildTime)}
      </div>
      <div>
        — Source &amp; Desktop-Build: <span className="font-mono">com.findatex/findatex-validator</span>
      </div>
    </div>
    ```

  - When `info.commit` is empty (plugin didn't run): show only `v{info.version}`
    on the first line; gracefully degrade.
  - `formatBuildDate` formats ISO-8601 → `YYYY-MM-DD` (date only, locale-neutral).
    Use `new Date(iso).toISOString().slice(0, 10)`; covers the German users'
    expectation of an ISO-style date.

## Files touched

```
web-app/pom.xml                                                   (add plugin)
web-app/src/main/java/com/findatex/validator/web/api/BuildInfoResource.java   (new)
web-app/src/main/java/com/findatex/validator/web/dto/BuildInfo.java           (new)
web-app/src/test/java/com/findatex/validator/web/api/BuildInfoResourceTest.java (new)
web-app/src/main/frontend/src/api.ts (or equivalent existing client)          (add fetchBuildInfo)
web-app/src/main/frontend/src/App.tsx                                         (footer change)
```

No changes outside `web-app/`. No changes to `core/` or `javafx-app/`.

## Verification

1. `mvn -pl web-app -am test` — green, including the new `BuildInfoResourceTest`.
2. `mvn -pl web-app -am -DskipTests package` — verify `web-app/target/classes/git.properties`
   exists and contains the three keys.
3. `mvn -pl web-app -am quarkus:dev` + `npm run dev` — open browser, footer shows
   the new line. With a clean working tree: no `-dirty`. After `touch a.tmp`:
   rebuild + reload → `-dirty` suffix appears.
4. Docker build (`docker build -t findatex-validator-web:1.0.0 .`) — confirm
   `git.properties` is present in the produced image (multi-stage Dockerfile
   currently does `COPY` from the build stage; verify the resource lands in
   the runtime classpath).
