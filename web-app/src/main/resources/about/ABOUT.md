# FinDatEx Validator — Web

**Version 1.0.0** · Karl Kauc · Apache License 2.0

Source code: <https://github.com/karlkauc/findatex-validator>

The web edition validates FinDatEx data templates (TPT, EET, EMT, EPT)
against the official spec sheets — completeness, format conformance,
codification, and cross-field rules. Uploads are processed in memory and
deleted immediately after the response; reports are available for 5 minutes
via a single-use URL.

---

## Author

**Karl Kauc** — <karl.kauc@gmail.com>

## Project license

Apache License 2.0 — full text at
<https://www.apache.org/licenses/LICENSE-2.0>.

## Third-party libraries — Runtime (Backend, Java)

| Library | Version | License |
|---|---|---|
| Quarkus (`rest`, `rest-jackson`, `arc`, `smallrye-health`) | 3.34.6 | Apache-2.0 |
| JBoss SLF4J Logmanager bridge | (Quarkus BOM) | Apache-2.0 |
| Apache POI / `poi-ooxml` | 5.5.1 | Apache-2.0 |
| Apache Commons CSV | 1.14.1 | Apache-2.0 |
| Jackson Databind | 2.21.2 | Apache-2.0 |
| SLF4J API | 2.0.17 | MIT |
| Logback Classic | 1.5.32 | EPL-1.0 / LGPL-2.1 |
| Bucket4j Core | 8.10.1 | Apache-2.0 |
| Caffeine | 3.2.3 | Apache-2.0 |

## Third-party libraries — Runtime (Frontend, npm)

| Library | Version | License |
|---|---|---|
| React / React-DOM | 19.2.5 | MIT |
| `@tanstack/react-query` | 5.62.7 | MIT |
| `react-dropzone` | 15.0.0 | MIT |
| `react-markdown` | 10.1.0 | MIT |
| `remark-gfm` | 4.0.0 | MIT |
| `lucide-react` | 1.11.0 | ISC |

## Third-party libraries — Build & Test

| Library | License |
|---|---|
| Apache Maven | Apache-2.0 |
| `frontend-maven-plugin` | Apache-2.0 |
| Quarkus JUnit5 | Apache-2.0 |
| RestAssured 6.0.0 | Apache-2.0 |
| AssertJ Core 3.27.7 | Apache-2.0 |
| Vite 8 | MIT |
| Tailwind CSS 4 | MIT |
| TypeScript 6 | Apache-2.0 |
| Vitest 4 + Testing Library | MIT |

---

Library list reflects the bundle as of April 2026. Refer to the project's
`pom.xml` and `package.json` files on GitHub for the current authoritative
dependency set.
