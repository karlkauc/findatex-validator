# External Validation — GLEIF LEI & OpenFIGI ISIN

Spec date: 2026-04-27
Author: TPT V7 Validator team
Status: approved (brainstorming)

## 1. Summary

Add optional online validation of LEIs (against GLEIF) and ISINs (against
OpenFIGI) to the TPT V7 Validator. Today both `IsinRule` and `LeiRule` only
verify the algorithmic checksum (Luhn / mod-97). With this feature, the
validator additionally confirms that the identifier exists in the
authoritative public register and — when the user opts in — cross-checks
selected TPT fields against the live record.

The feature is **off by default** and must remain fully usable in a
corporate environment behind an authenticating HTTP proxy (system proxy
with NTLM transparent fallback, manual proxy with stored credentials).

The implementation follows the proxy pattern established in the
`FreeXmlToolkit` project: NTLM is enabled before any HTTP traffic, JVM
proxy properties are explicitly cleared and then set based on a custom
detection chain (netsh → Windows Registry → environment variables → Java
`ProxySelector`/PAC/WPAD).

## 2. Goals

- Existence check for every LEI and ISIN in a TPT file against the
  authoritative public register.
- Optional, individually toggle-able cross-checks for LEI status,
  issuer name, issuer country, ISIN currency, and CIC ↔ OpenFIGI security
  type.
- Batch-friendly: caching across files and sessions, bulk API where
  available, polite throttling.
- Corporate-network ready: works with system proxy + NTLM out of the box;
  manual proxy with encrypted password as fallback.
- Soft failure: a failed online lookup never blocks the local validation
  run. The reason is surfaced as a single INFO finding plus a banner.

## 3. Non-goals

- ANNA paid lookup (not in scope; OpenFIGI covers the same need for free).
- "Trust all certs" SSL bypass (corporate MITM proxies install root CAs at
  OS level; Java 13+ uses the OS truststore by default).
- Tunable endpoint URLs in the UI; endpoints are hard-coded.
- Headless / CLI mode for online validation (existing CLI scope is
  unchanged; online validation is UI-only in this iteration).
- Live-streaming online findings into the table while the lookup is still
  running. The lookup is a blocking modal phase after local validation.
- Cross-checks beyond the explicitly listed sub-toggles (no fuzzy issuer
  search, no derived fields).

## 4. User stories

- *As a Solvency II reporting analyst,* I want the validator to flag LEIs
  that no longer exist in GLEIF, so I can correct fund data before
  submission.
- *As a data quality reviewer,* I want to detect ISINs that do not exist
  in OpenFIGI, so I can spot transposition errors that pass the Luhn
  check.
- *As a corporate IT user,* I want the validator to use my Windows proxy
  configuration with NTLM transparently, so I do not have to enter
  credentials.
- *As a batch processor,* I want a persistent cache so that re-validating
  the same file (or another file with overlapping issuers) does not
  re-issue the same online queries.
- *As a security officer,* I want the option to disable online validation
  entirely (default) so that no instrument identifiers leak to external
  services.

## 5. Architecture

### 5.1 New packages and files

```
com.tpt.validator
├── config/                              ← NEW
│   ├── AppSettings.java                 record: external + proxy
│   ├── SettingsService.java             load/save settings.json (singleton)
│   └── PasswordCipher.java              AES wrapper, machine-bound (port from FXT)
│
├── external/                            ← NEW
│   ├── proxy/
│   │   ├── ProxyConfig.java             record: mode, host, port, user, pass
│   │   ├── SystemProxyDetector.java     port from FXT
│   │   ├── ProxyAuthenticator.java      port from FXT
│   │   ├── HttpOnlyProxySelector.java   port from FXT (SOCKS→HTTP fix)
│   │   └── ProxyService.java            bootstrap + mode switching
│   ├── http/
│   │   ├── HttpClientFactory.java       singleton java.net.http.HttpClient
│   │   ├── RateLimiter.java             token bucket per service
│   │   └── HttpExecutor.java            retry/backoff for 429/5xx
│   ├── cache/
│   │   └── JsonCache.java               generic, TTL, persistent JSON
│   ├── gleif/
│   │   ├── GleifClient.java             GLEIF L1 API
│   │   └── LeiRecord.java               record(lei, legalName, country, status)
│   ├── openfigi/
│   │   ├── OpenFigiClient.java          POST /v3/mapping (bulk up to 100)
│   │   └── IsinRecord.java              record(isin, name, currency, exchCode, marketSector)
│   └── ExternalValidationService.java   orchestrator
│
├── validation/rules/external/           ← NEW
│   ├── LeiOnlineRule.java
│   ├── IsinOnlineRule.java
│   └── ExternalLookupSummaryRule.java   one INFO finding per failed service
│
└── ui/                                  ← EXTENDED
    ├── MainController.java              + master toggle + ⚙ button
    ├── SettingsController.java          ← NEW (two tabs)
    ├── SettingsView.fxml                ← NEW
    └── LookupProgressDialog.fxml        ← NEW (modal, cancel)
```

### 5.2 Maven dependency change

Add `com.fasterxml.jackson.core:jackson-databind` (version `2.18.2`) for
JSON parsing of `settings.json`, the cache files, and GLEIF / OpenFIGI
responses. No other new dependencies; HTTP uses JDK
`java.net.http.HttpClient`. Tests use the JDK's
`com.sun.net.httpserver.HttpServer` as a stub server.

### 5.3 Layering rules

- `external/` depends on `config/` (reads `AppSettings`).
- `validation/rules/external/` depends on `external/` (calls clients via
  `ExternalValidationService`) and on existing `validation/` (extends
  `Rule`-style finding production).
- `ui/` depends on `config/` (Settings dialog) and on `external/` (test
  connection buttons, master toggle).
- `external/` does **not** depend on `ui/` or `validation/`. The new
  online rules live in `validation/rules/external/` and are explicitly
  invoked by `MainController`, not registered in `RuleRegistry`.

### 5.4 Why the new rules are not in `RuleRegistry`

`RuleRegistry.build(...)` returns `List<Rule>` whose `evaluate(ctx)` is
called sequentially in `ValidationEngine.validate(...)`. That contract is
synchronous and pure. Online rules need:

- A bulk dedupe step across all rows of all eight LEI/ISIN columns.
- Network I/O with retry, throttle, cache.
- A cancel signal from the UI.
- Progress reporting.

Forcing those into the existing `Rule` interface would either turn every
`evaluate` call into N HTTP requests (catastrophic) or smuggle bulk state
into `ValidationContext` (leaky). Cleaner cut: local validation (sync,
pure) is phase 1, external validation (I/O, batched, cancellable) is
phase 2, run from `MainController` after `ValidationEngine.validate(...)`
returns.

## 6. Settings

### 6.1 File location

- Linux / macOS: `~/.config/tpt-validator/settings.json`
- Windows: `%APPDATA%\tpt-validator\settings.json`
- Cache directory (default): `<settings-dir>/cache/`, override path
  configurable in settings.

### 6.2 Schema

```json
{
  "external": {
    "enabled": false,
    "lei": {
      "enabled": true,
      "checkLapsedStatus": true,
      "checkIssuerName": false,
      "checkIssuerCountry": false
    },
    "isin": {
      "enabled": true,
      "openFigiApiKey": "",
      "checkCurrency": false,
      "checkCicConsistency": false
    },
    "cache": {
      "ttlDays": 7,
      "directory": ""
    }
  },
  "proxy": {
    "mode": "SYSTEM",
    "manual": {
      "host": "",
      "port": 0,
      "user": "",
      "passwordEncrypted": "",
      "nonProxyHosts": "localhost|127.0.0.1"
    }
  }
}
```

- `external.enabled = false` is the project default. The master toggle in
  the main window mirrors this value.
- `proxy.mode`: `SYSTEM` (default; NTLM transparent), `MANUAL` (with
  credentials), `NONE` (direct connection).
- `passwordEncrypted`: Base64-encoded AES ciphertext. Key derived per
  `PasswordCipher` from machine-bound inputs (analogous to FXT).
- Missing fields fall back to defaults; missing file is created at first
  save. No schema version field in V1.
- Endpoints (GLEIF L1 base, OpenFIGI base) are hard-coded constants in
  the respective client classes — not user-editable. Concretely:
  `https://api.gleif.org/api/v1/lei-records` and
  `https://api.openfigi.com/v3/mapping`.
- `passwordEncrypted` is bound to the machine the cipher key is derived
  on. If the user copies `settings.json` to another machine, decryption
  fails silently; `ProxyService` logs a WARN, the password is treated
  as empty, and the proxy mode falls back to behaving as if the
  password field were left blank. The user must re-enter it on the new
  machine.

### 6.3 SettingsService contract

```java
public final class SettingsService {
    public static SettingsService getInstance();
    public AppSettings getCurrent();         // immutable snapshot
    public void update(AppSettings next);    // atomic write (tmp + rename)
}
```

Single-writer, multiple-reader; reads are lock-free via volatile snapshot.

## 7. Validation flow

### 7.1 Sequence on `Validate` click

1. `MainController.validate()` runs on the FX thread; spawns a
   `Task<List<Finding>>` on a background thread.
2. **Phase 1 — local:** `ValidationEngine.validate(file, profiles)`
   produces `localFindings` (today's behaviour, unchanged).
3. If `external.enabled = false`: enrich, render, done.
4. **Phase 2 — external:** open modal `LookupProgressDialog`. The same
   `Task` continues with `ExternalValidationService.run(file,
   localFindings)`:
   - **a.** Collect distinct identifiers, with field/row context, from
     the authoritative columns (see 7.2). Skip values that fail the local
     checksum check — they are already error-reported and would only
     produce 404s.
   - **b.** Cache lookup per value (`JsonCache`). Entries older than
     the configured TTL count as misses and are evicted lazily on
     access.
   - **c.** Cache misses are batched: OpenFIGI `POST /v3/mapping` with
     up to 100 ISINs per request; GLEIF
     `GET /api/v1/lei-records?filter[lei]=...` with up to 200 LEIs per
     request.
   - **d.** Throttle via `RateLimiter`: GLEIF 8 req/s, OpenFIGI 4 req/s
     without API key, 100 req/s with key (token-bucket; values are
     constants, not user-tunable in V1).
   - **e.** Retry on `429` and `5xx`: 3 attempts, exponential backoff
     2s/4s/8s (`HttpExecutor`).
   - **f.** Persist successful responses to the cache.
   - **g.** Apply `LeiOnlineRule` and `IsinOnlineRule` to produce
     `onlineFindings`.
5. Service-level failure (all retries exhausted, network down, proxy
   denied) → emit one INFO finding `EXTERNAL/<service>-UNAVAILABLE` plus
   set a status banner in the main window. No per-identifier flooding.
6. On `Task.cancel`: stop in-flight HTTP via `HttpClient` future
   cancellation, render partial findings, add INFO finding
   `EXTERNAL/CANCELLED`.
7. Merge `localFindings + onlineFindings`, run `FindingEnricher.enrich(...)`,
   refresh table and quality report.

### 7.2 Identifier sources in the file

- **ISINs:** field 14 when field 15 = "1"; field 68 when field 69 = "1".
- **LEIs:** fields 47/48, 50/51, 81/82, 84/85, 115/116, 119/120, 140/141.

These are exactly the same coordinates `RuleRegistry` uses today for
`IsinRule` / `LeiRule`. Nothing else is queried online.

### 7.3 Finding identity

Online findings use IDs that mirror the existing
`<RULE>/<numKey>/<typeKey>` convention so reports remain consistent:

| ID | Severity | When |
|---|---|---|
| `LEI-LIVE/<num>/<type>` | ERROR | LEI passes mod-97 but is unknown to GLEIF |
| `LEI-LIVE-STATUS/<num>/<type>` | WARNING | GLEIF status is `LAPSED` / `RETIRED` (sub-toggle `checkLapsedStatus`) |
| `LEI-LIVE-NAME/<num>/<type>` | WARNING | TPT issuer name (field 46) and GLEIF legal name differ (sub-toggle `checkIssuerName`) |
| `LEI-LIVE-COUNTRY/<num>/<type>` | WARNING | TPT issuer country (field 52) and GLEIF legal-address country differ (sub-toggle `checkIssuerCountry`) |
| `ISIN-LIVE/<num>/<type>` | ERROR | ISIN passes Luhn but is unknown to OpenFIGI |
| `ISIN-LIVE-CCY/<num>/<type>` | WARNING | TPT quotation currency (field 21) and OpenFIGI currency differ (sub-toggle `checkCurrency`) |
| `ISIN-LIVE-CIC/<num>/<type>` | WARNING | TPT CIC and OpenFIGI security type are inconsistent (sub-toggle `checkCicConsistency`) |
| `EXTERNAL/GLEIF-UNAVAILABLE` | INFO | GLEIF could not be reached for this run |
| `EXTERNAL/OPENFIGI-UNAVAILABLE` | INFO | OpenFIGI could not be reached for this run |
| `EXTERNAL/CANCELLED` | INFO | User cancelled the online phase |

### 7.4 Name-comparison heuristic (sub-toggle `checkIssuerName`)

To avoid flooding tickets with cosmetic differences ("BlackRock Inc" vs
"BlackRock, Inc."), the comparison is intentionally strict: a finding is
emitted only when, after Unicode-normalising both strings (NFKD,
removing diacritics, lowercasing, stripping `Inc/Ltd/SA/AG/...` suffixes
and all non-alphanumeric characters), the two strings are not equal.
Implementation in a small helper `IssuerNameComparator` so the rule itself
stays declarative.

### 7.5 Cancel semantics

`Task.cancel(true)` propagates to the `ExternalValidationService` loop,
which stops scheduling new HTTP requests. In-flight requests are best-effort
cancelled via the `HttpResponse` future; any responses that have already
arrived are still consumed and cached. The user sees the partial
findings in the table; an INFO finding `EXTERNAL/CANCELLED` is added.

## 8. Proxy bootstrap

### 8.1 Order of operations

In `App.start(...)`, before any HTTP client is constructed:

1. Read settings via `SettingsService.getInstance().getCurrent()`.
2. `ProxyService.enableNtlmAuthentication()` — enables NTLM tunneling
   schemes, installs the corporate `Authenticator`, prefers IPv4. Must
   run before the first HTTP request, otherwise NTLM is silently
   unavailable.
3. `ProxyService.clearJvmProxyProperties()` — clears
   `http.proxyHost/Port`, `https.proxyHost/Port`, `http.nonProxyHosts`,
   `https.nonProxyHosts`. The JVM auto-populates these from
   `HTTP_PROXY` / `HTTPS_PROXY` environment variables at startup, which
   in corporate setups is regularly the wrong value (e.g. a `curl`
   variable that does not match the WPAD-resolved Windows proxy). We
   start clean and re-set deterministically.
4. Apply mode-specific configuration:
   - `SYSTEM`: `SystemProxyDetector.detectSystemProxy()` returns a
     `ProxyConfig` from netsh / Registry / env / `ProxySelector`. If
     found, `SystemProxyDetector.configureProxy(host, port)` sets the
     JVM properties. If nothing found, leave the properties cleared and
     let `ProxySelector.getDefault()` handle PAC/WPAD at request time.
   - `MANUAL`: set `http.proxyHost/Port` and `https.proxyHost/Port` from
     stored config, install a `ProxyAuthenticator` with decrypted
     credentials.
   - `NONE`: properties stay cleared (already done in step 3).
5. UI starts.

### 8.2 HttpClientFactory

A single `HttpClient` instance for the whole app lifetime, lazily
created:

- `connectTimeout = 10s`, request `timeout = 15s`.
- `followRedirects = NORMAL`.
- `version = HTTP_1_1` (HTTP/2 trips many corporate MITM proxies; FXT
  experience).
- `proxy = ProxySelector.getDefault()` — the JVM-property bridge fills
  the selector's data.

A method `HttpClientFactory.rebuild()` is invoked when the user changes
proxy settings, so the next `get()` returns a fresh client. The cache is
not invalidated (LEI / ISIN data does not depend on proxy mode).

### 8.3 TLS

The corporate MITM situation is handled by the OS truststore (Java 13+
on Windows uses `windows-ROOT`, on macOS uses `KeychainStore`). The user
can supply a custom truststore via standard
`-Djavax.net.ssl.trustStore=...` flags, documented in the README. There
is no in-app "trust all certs" toggle — that would be a footgun in a
validator.

## 9. UI

### 9.1 Main window

A single new section between the profile checkboxes and the `Validate`
button:

```
External:  [ ] Online validation (GLEIF + OpenFIGI)         [⚙]
           ⓘ Disabled — runs locally only
```

- Checkbox is bound to `external.enabled`; toggling persists immediately.
- Gear button opens the modal Settings dialog.
- A status banner under the validate button shows online-phase
  failures from the most recent run, e.g.
  `⚠ GLEIF unreachable — see findings tab for details`. The banner
  clears at the next validate.

### 9.2 Settings dialog — "External Validation" tab

Three grouped panels:

- **GLEIF (LEI)**: enable checkbox + three sub-toggles
  (`checkLapsedStatus`, `checkIssuerName`, `checkIssuerCountry`) +
  `[Test connection]` button.
- **OpenFIGI (ISIN)**: enable checkbox + API key text field +
  two sub-toggles (`checkCurrency`, `checkCicConsistency`) +
  `[Test connection]` button.
- **Cache**: TTL spinner (days) + directory chooser + `[Clear cache now]`
  button (deletes both `lei-cache.json` and `isin-cache.json`).

### 9.3 Settings dialog — "Network / Proxy" tab

- Three radio buttons for `proxy.mode`.
- A panel (enabled only when `MANUAL` is selected) with host, port,
  user, password (masked, `(encrypted on disk)` hint), no-proxy hosts.
- A diagnostics line showing the detected proxy and detection method:
  `Detected: proxy.firma.local:8080 (via netsh winhttp)`.
- A `[Test connection]` button per service (GLEIF, OpenFIGI) that hits
  a single, no-side-effect endpoint and shows status, latency, and any
  proxy-auth error in a small inline result row. Concretely: GLEIF →
  `GET /api/v1/lei-records?page[size]=1`; OpenFIGI →
  `GET /v3/mapping/values/exchCode` (lightweight reference list). This
  is the corporate-support workhorse.

### 9.4 Lookup progress dialog

Modal during phase 2:

```
Local validation: complete (1.2s, 87 findings)

GLEIF lookup:    [████████░░░░░░░░░░░] 234 / 478 LEIs
OpenFIGI lookup: [██████░░░░░░░░░░░░░] 198 / 532 ISINs

Cache hits:  423 / 1010 (42%)

                                              [Cancel]
```

Updates via `Task.updateMessage(...)` and `Task.updateProgress(...)`.

## 10. Error handling

### 10.1 Categories

- **Service unavailable** (network down, DNS fail, all retries failed):
  one INFO finding per service, banner in main window. Local findings
  are unaffected.
- **Auth required** (407 from proxy, 401 from upstream): same as service
  unavailable, but the finding message names the failure cause
  ("proxy authentication failed").
- **Per-identifier 404 from OpenFIGI / unknown LEI from GLEIF**: that is
  the normal path that produces a `-LIVE` finding. Not a service error.
- **Timeout on a single request**: counts as one retry attempt; if all
  three attempts time out, the request is dropped and the affected
  identifier(s) get *no* online finding (they are not visible to the
  user as "checked"). Per-identifier finding-of-failure is intentionally
  not emitted to avoid finding floods on flaky links — the service-level
  banner covers this case once.
- **Cache I/O failure** (e.g. disk full, permission denied): logged
  at WARN, lookup proceeds without persistence (in-memory only for the
  rest of the session). Not surfaced as a finding.

### 10.2 Cancellation

User cancels the modal → in-flight HTTP futures are cancelled, partial
results are merged, INFO finding `EXTERNAL/CANCELLED` is added.

## 11. Testing

| Scope | What | How |
|---|---|---|
| `JsonCache` | TTL expiry, persistence round-trip, concurrent access | Unit, `@TempDir` |
| `ProxyService` / `SystemProxyDetector` | Parsers for netsh / Registry / env strings; mode switching clears and re-sets properties | Unit with string fixtures (analogous to FXT `SystemProxyDetectorTest`) |
| `PasswordCipher` | Encrypt / decrypt round-trip; failure on tampered ciphertext | Unit |
| `RateLimiter` | Token-bucket maths, fairness | Unit with mock clock |
| `HttpExecutor` | Retry on 429 / 5xx; success on 2xx; give-up after 3 attempts | Local `com.sun.net.httpserver.HttpServer` returning canned status codes |
| `GleifClient` / `OpenFigiClient` | Response parsing, batching, paging | Local stub server with recorded JSON in `src/test/resources/external/` |
| `LeiOnlineRule` / `IsinOnlineRule` | Existence / status / sub-toggle findings | Test-double clients, fixed records, no Mockito |
| `ExternalValidationService` | Dedupe, cache hit path, service-failure → INFO finding, cancel | Test-double clients, in-memory cache |
| `SettingsService` | Default file creation, missing-key migration, atomic write | `@TempDir` |
| Optional integration | Real API calls | `@EnabledIfSystemProperty(named="run.online.tests", matches="true")` — not on default `mvn test` |

Not covered (consistent with current repo policy):

- JavaFX UI (Settings dialog, progress dialog).
- Real Windows + NTLM behaviour. We log generously at DEBUG so users can
  diagnose; the in-app `[Test connection]` buttons are the field tool.

## 12. Implementation order

1. `config/`: `AppSettings`, `SettingsService`, `PasswordCipher`.
2. `external/proxy/`: ports of FXT (`SystemProxyDetector`,
   `ProxyAuthenticator`, `HttpOnlyProxySelector`) + new `ProxyService`.
3. `external/http/`: `HttpClientFactory`, `RateLimiter`, `HttpExecutor`.
4. `external/cache/`: `JsonCache`.
5. `external/gleif/` and `external/openfigi/`: clients + records,
   tested against the JDK stub server.
6. `validation/rules/external/`: `LeiOnlineRule`, `IsinOnlineRule`,
   `ExternalLookupSummaryRule`; `ExternalValidationService` orchestrator.
7. UI: `SettingsView.fxml` + `SettingsController`, master toggle in
   `MainView.fxml` / `MainController`, `LookupProgressDialog.fxml`.
8. Wire phase 2 into `MainController.validate(...)`; add banner status
   handling.

Each step is independently testable. After step 5 the network plumbing
is fully exercised against a stub server; UI work in step 7 only assembles
existing pieces.

## 13. Open follow-ups (out of scope for V1)

- A CLI flag `--online` to use the new validation in headless batch
  pipelines (currently UI-only).
- Pluggable name-similarity (e.g. a Levenshtein threshold) for
  `checkIssuerName`.
- Caching the GLEIF / OpenFIGI raw response bodies for offline replay
  in support cases.
- ANNA paid lookup as an alternative to OpenFIGI.
- Network proxy auto-detection refresh while the app is running (today
  it re-detects only on settings change).
