# Technical Debt Log

Known improvements deferred for focus or time constraints.
This is a **living document** — add entries as decisions are made to defer work,
close them when resolved.

## How to Use This Document

- **When you notice an issue that shouldn't block the current PR**: add an entry here instead of leaving a `TODO` comment in code.
- **When starting a new PR**: scan this document for items whose "target resolution" matches.
- **When closing an item**: change status to `Resolved`, add the PR number and date.

## Entry Format

Each entry has a stable ID (`TD-NNN`), never renumbered even when resolved.

| Field | Description |
|-------|-------------|
| What | Concrete description of the issue |
| Why deferred | Rationale for not fixing immediately |
| Affected | Files / modules / areas touched |
| Severity | Low / Medium / High |
| Target resolution | Which PR or window to address this |
| Status | Open / Resolved |

---

## Open Items

*(No open items — all tech debt resolved)*

---

### TD-002: `getCityById` suspend overhead inside observe chain

**Introduced**: PR 04 (Stage 3 fix)
**Status**: Resolved — PR 05
**Severity**: Low

**What**

`ObserveSelectedCityUseCase` called `cityRepository.getCityById(id)` inside `Flow.map { ... }` every time `selectedCityId` emitted. This was a one-shot suspend query — it didn't re-run when the selected city's row was updated in-place (same id, updated name/coords), causing a stale City object to remain in the observe chain.

**Affected**

- `:core:domain/usecase/ObserveSelectedCityUseCase.kt`
- `:core:domain/usecase/ObserveSelectedCityWeatherUseCase.kt`

**Resolution (PR 05)**

Both use cases now use `combine(selectedCityId, cityRepository.observeSavedCities())` and resolve the city by filtering the live list: `cities.firstOrNull { it.id == id } ?: DefaultCity.TAIPEI`. Since `observeSavedCities()` is backed by a Room DAO `Flow`, any in-place upsert (including `current_location` re-detection) triggers a new emission and the City object is always fresh.

Note: Did NOT add the proposed `observeCityById(id)` DAO method. Observing the full saved-cities list and filtering by id is sufficient for current scale and avoids adding a new DAO query. A per-row `Flow<City?>` observe could be added as a future performance optimization if the city list grows large, but it is not a correctness issue.

---

### TD-003: Replace deprecated `Icons.Filled.HelpOutline` with `AutoMirrored` variant

**Introduced**: PR 02 (compile warning surfaced during PR 04 final build)
**Status**: Resolved — PR 06
**Resolution date**: 2026-04-27
**Severity**: Low

**What**

`core/designsystem/component/WeatherIcon.kt` uses `Icons.Filled.HelpOutline` for the `WeatherCondition.UNKNOWN` case. Material Icons deprecated this in favor of `Icons.AutoMirrored.Filled.HelpOutline`, which mirrors correctly under RTL locales.

**Affected**

- `:core:designsystem/component/WeatherIcon.kt`

**Resolution (PR 06)**

Replaced `Icons.Filled.HelpOutline` with `Icons.AutoMirrored.Filled.HelpOutline` in `core/designsystem/component/WeatherIcon.kt`. Build no longer emits the deprecation warning. RTL locales now render the icon correctly mirrored.

---

## Resolved Items

| ID | Title | Resolved in |
|----|-------|-------------|
| TD-001 | Data source classes should be interfaces | PR 07 |
| TD-002 | `getCityById` suspend overhead in observe chain | PR 05 |
| TD-003 | Replace deprecated `HelpOutline` with `AutoMirrored` | PR 06 |

---

## Index

| ID | Title | Severity | Target | Status |
|----|-------|----------|--------|--------|
| TD-001 | Data source classes should be interfaces | Low | PR 07 | Resolved |
| TD-002 | `getCityById` suspend overhead in observe chain | Low | PR 05 | Resolved |
| TD-003 | Replace deprecated `HelpOutline` with `AutoMirrored` | Low | PR 06 | Resolved |