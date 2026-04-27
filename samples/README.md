# FinDatEx sample fixtures

Generator-driven example files for every template the validator
understands. One sub-folder per template, each with its own README
listing the individual files.

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
