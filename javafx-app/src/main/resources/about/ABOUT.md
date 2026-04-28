# FinDatEx Validator — Desktop

**Version 1.0.0** · Karl Kauc · Apache License 2.0

Source code: <https://github.com/karlkauc/findatex-validator>

The desktop edition validates FinDatEx data templates (TPT, EET, EMT, EPT)
against the official spec sheets — completeness, format conformance,
codification, and cross-field rules. Files never leave your machine.

---

## Author

**Karl Kauc** — <karl.kauc@gmail.com>

## Project license

Apache License 2.0 — see the bundled `LICENSE` file or the canonical text at
<https://www.apache.org/licenses/LICENSE-2.0>.

## Third-party libraries — Runtime

| Library | Version | License |
|---|---|---|
| OpenJFX `javafx-controls`, `javafx-fxml` | 21.0.5 | GPL-2.0 + Classpath Exception |
| Apache POI / `poi-ooxml` | 5.5.1 | Apache-2.0 |
| Apache Commons CSV | 1.14.1 | Apache-2.0 |
| Jackson Databind | 2.21.2 | Apache-2.0 |
| SLF4J API | 2.0.17 | MIT |
| Logback Classic | 1.5.32 | EPL-1.0 / LGPL-2.1 |
| CommonMark Java + GFM-Tables ext. | 0.28.0 | BSD-2-Clause |

## Third-party libraries — Build & Test

| Library | License |
|---|---|
| Apache Maven, jpackage | Apache-2.0 |
| `javafx-maven-plugin` | BSD-3-Clause |
| JUnit Jupiter 6.0.3 | EPL-2.0 |
| AssertJ Core 3.27.7 | Apache-2.0 |

---

Library list reflects the bundle as of April 2026. Refer to the project's
`pom.xml` files on GitHub for the current authoritative dependency set.
