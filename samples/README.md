# FinDatEx sample fixtures

Generator-driven example files for every template the validator
understands. One sub-folder per template, each with its own README
listing the individual files.

Two kinds of file live here:

* **`00_showcase.xlsx`** — the demo behind *"No file at hand? Try an example"*
  on the web app. A full delivery (60 TPT positions across three funds,
  25 share classes for EET/EMT/EPT) with a curated spread of realistic
  defects. `SampleFiles` in `web-app` serves exactly these four.
* **`01_…` and up** — one-rule-family-each regression fixtures, kept small on
  purpose so a failing assertion points at one rule.

Adding a file that has to reach the container means widening three places, all
of which fail silently: the `<resources>` include in `web-app/pom.xml`, the
negation in `.dockerignore`, and the `SampleFiles` map.

| Folder | Template | Generator | JUnit |
|--------|----------|-----------|-------|
| [`tpt/`](tpt/) | TPT V7.0 | `tools/build_examples.py` | `ExampleSamplesTest` |
| [`eet/`](eet/) | EET V1.1.3 | `tools/build_eet_samples.py` | `EetExampleSamplesTest` |
| [`emt/`](emt/) | EMT V4.3 | `tools/build_emt_samples.py` | `EmtExampleSamplesTest` |
| [`ept/`](ept/) | EPT V2.1 | `tools/build_ept_samples.py` | `EptExampleSamplesTest` |

Regenerate everything:

```bash
python3 tools/build_examples.py
python3 tools/build_eet_samples.py
python3 tools/build_emt_samples.py
python3 tools/build_ept_samples.py
```

Open any file via the JavaFX UI (`mvn javafx:run` → switch to the
matching tab → *Browse…*) or run all sample tests with
`mvn -Dtest='*ExampleSamplesTest' test`.
