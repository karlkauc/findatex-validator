# Per-Fund Score UI — Design

**Date:** 2026-05-01
**Status:** Approved (brainstorming phase)
**Predecessor:** `feat(core): multi-fund TPT validation` (commit `7da2730`)

## Context

Multi-fund TPT validation already populates `QualityReport.perFundScores: Map<FundKey, Map<ScoreCategory, Double>>` (only when `groups.size() > 1`). No surface currently renders that data. This design adds rendering across the three existing surfaces — Excel report, JavaFX desktop, web React frontend — using the same shape and grammar that already exists for per-profile scores.

## Approach

Mirror the existing per-profile rendering pattern in each surface. Per-fund scores share the same five `ScoreCategory` columns (Mandatory, Format, Closed-List, Cross-Field, Overall) plus three identifying columns (Portfolio ID, Portfolio Name, Valuation Date). Rendering is conditional on `perFundScores.size() > 1` — single-fund files render unchanged.

Rejected alternatives:

- **Drill-down** (per-fund row clickable → fund-specific findings panel). Larger scope, introduces new navigation; can be a future iteration if useful.
- **Profile × Fund matrix** (nested per-profile inside per-fund). High data density but visually heavy and implementation-complex; YAGNI.

## Excel — new "Per Fund" sheet

`XlsxReportWriter.writePerFund(...)` adds a new sheet between the existing `Scores` and `Findings` sheets. Inserted only when `report.perFundScores().size() > 1`.

**Columns:** `Fund #`, `Portfolio ID`, `Portfolio Name`, `Valuation Date`, `Mandatory`, `Format`, `Closed-List`, `Cross-Field`, `Overall`.

**Rows:** one per fund, in the same order as `perFundScores` (which is a `LinkedHashMap` preserving insertion order from `FundGrouper.group(...)` — i.e. the file's encounter order).

**Styling:** percentage cells use the same color-coded styling as the existing `Scores` sheet (`ok` ≥ 0.95, `warn` ≥ 0.80, `err` < 0.80).

**Tests:** new test case in `XlsxReportWriterTest` loads `13_multi_fund_with_errors.xlsx`, asserts the `Per Fund` sheet exists, header row matches, and the body has exactly 3 data rows (FR / DE / LU) in encounter order.

## JavaFX — per-fund section in result pane

`TemplateTabController.renderReport(...)` already renders an overall scores row and a per-profile section. After the per-profile section, append a `Per Fund Scores` section.

**Component:** a `TableView<FundScoreRow>` (new private record `FundScoreRow(String fundId, String fundName, String valuationDate, double mandatory, double format, double closed, double cross, double overall)` inside the controller).

**Visibility:** the section's parent `VBox` (or wrapping container) gets `setVisible(perFundScores.size() > 1)` and `setManaged(...)` so it is fully removed from layout for single-fund files.

**Styling:** existing `score-good` / `score-warn` / `score-bad` CSS classes from the per-profile table are reused on cell factories; no new CSS classes needed.

**FXML:** add an `fx:id="perFundContainer"` `VBox` with an `fx:id="perFundTable"` `TableView` inside the result pane FXML. If FXML is not the right place (the per-profile table is built programmatically in the controller), the per-fund table is built the same way and added programmatically.

**Tests:** existing `javafx-app` test infra builds a `TestFx` integration test that loads `13_multi_fund_with_errors.xlsx`, validates, and asserts the per-fund table contains 3 rows. Defer if `javafx-app/src/test/java` does not yet have TestFx scaffolding — in that case, document this as a manual verification step (`mvn -pl javafx-app javafx:run`, open the multi-fund fixture, eyeball the per-fund block).

## Web — DTO + endpoint pass-through + React component

### DTO
New record in `web-app/src/main/java/com/findatex/validator/web/dto/`:

```java
public record PerFundScoreDto(
    String portfolioId,
    String portfolioName,
    String valuationDate,
    List<ScoreDto> scores) {}
```

`ValidationResponse` gains a new field `List<PerFundScoreDto> perFundScores`, populated from `QualityReport.perFundScores()` by `ValidationOrchestrator` (or whichever class currently maps `QualityReport` → DTOs).

For single-fund files, `perFundScores` is the empty list. The frontend uses `array.length > 1` (or simply `> 0`, since the backend gates already) to decide rendering.

### React component
New file `web-app/src/main/frontend/src/components/PerFundScores.tsx`:

```tsx
type Props = { perFundScores: PerFundScoreDto[] };
export function PerFundScores({ perFundScores }: Props) { ... }
```

Renders nothing when `perFundScores.length === 0`. Otherwise renders a single `Card` (matching existing styling) with a header "Per Fund" and a table: one column per `ScoreCategory`, one row per fund. Each score cell is a `<ScoreBadge label={category} percentage={value*100} />` reusing the existing component.

Inserted in the result page after the existing top-level `ScoreBadge` row.

### Tests
- `web-app/src/test/java/...ValidationResourceTest.java` (or equivalent): existing test loading a multi-fund fixture asserts `response.perFundScores` has 3 entries with the expected portfolio IDs.
- `web-app/src/main/frontend/src/components/PerFundScores.test.tsx`: vitest cases for empty array (renders nothing), single fund (renders nothing — backend should already gate), 3 funds (renders 3 rows with expected IDs).

## Test fixtures

Reuse `samples/tpt/13_multi_fund_with_errors.xlsx`. No new fixtures.

## Out of scope

- Per-fund **findings** drill-down (which findings belong to which fund) — already partially solved via `Finding.context().portfolioId()` but not surfaced as a filter UI. Future iteration.
- Per-fund **profile** breakdown (Profile × Fund matrix). Future iteration if a real user need surfaces.
- EET / EMT / EPT multi-fund. Their aggregate rules are mechanical-only today; if multi-fund support is added there, this rendering automatically applies (no per-template UI changes needed).
- A "fund picker" / single-fund-focus mode in the UI. The current spec is "show all funds in one view".

## Verification

End-to-end manual smoke test (one per surface):

1. **Excel:** `mvn -pl javafx-app javafx:run` → load `13_multi_fund_with_errors.xlsx` → export Excel report → open the XLSX, confirm `Per Fund` sheet has 3 data rows with the right portfolio IDs and color-coded scores.
2. **JavaFX:** same fixture, eyeball the per-fund block in the result pane — Fund A green, Fund B yellow/red on Cross-Field/Format, Fund C yellow on Mandatory.
3. **Web:** `mvn -pl web-app -am quarkus:dev` + frontend dev server → upload `13_multi_fund_with_errors.xlsx` → see the per-fund card with 3 rows in the result pane.

Automated:
- `mvn test` (full reactor) green ≥ 670 tests (current 668 + at least 2 new core/web tests).
- `(cd web-app/src/main/frontend && npm test)` green for `PerFundScores.test.tsx`.

## Risks / open points

- **JavaFX TestFx scaffolding** may not exist in `javafx-app/src/test/`. If absent, the JavaFX render is verified manually (acceptable; the data path is already covered by `MultiFundTest`).
- **`ValidationResponse` DTO compatibility:** older clients ignoring unknown fields tolerate the new `perFundScores` field. Confirm the JSON serialization config does not throw on unknown fields when newer clients hit older servers (one-way addition is the only deployed direction).
- **Sheet ordering** in `XlsxReportWriter`: inserting between `Scores` and `Findings` requires creating sheets in that order or using the index-based `wb.setSheetOrder(...)`. Existing tests assert sheet names but not order; verify they still pass.
- **i18n:** the `Per Fund` label and column headers are currently hardcoded English in JavaFX/Excel/Web (matching the rest of the app, which is single-language English in code with German user-facing strings only in some places). No new translation work in scope.
