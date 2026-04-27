# AI Usage in This Project

This document is an honest account of how AI tools were used during the development of this Weather Forecast app, following the assignment's request for transparency about AI usage.

## Tools Used

| Tool | Purpose | Model |
|---|---|---|
| Claude (claude.ai web chat) | Architecture decisions, PR specs, code reviews | claude-opus-4-7 |
| Claude Code (CLI) | Per-PR implementation, refactoring, bug investigation | claude-sonnet-4-6 |
| Android Studio code completion | Boilerplate, imports, simple fills | built-in |

## High-Level Workflow

The project was built across 7 PRs, each following the same pattern:

1. **Architecture/spec phase** (web chat): produce a detailed spec document (`docs/prs/PR0N_*.md`) before any code is written — covering module changes, new types, data flow, edge cases, and emulator scenarios
2. **Implementation phase** (Claude Code in terminal): hand the spec to Claude Code, which executes the work in 2–3 stages with checkpoints between them
3. **Verification phase** (human + emulator): I test the changes manually on the emulator and review the diff on GitHub
4. **Retrospective phase** (back to web chat): fill in the "Post-PR Retrospective" section of the spec doc, which feeds later PRs and this document

The split deliberately puts **architectural judgment** in the slower, more deliberate channel (web chat) and **execution** in the faster channel (CLI).

## How AI Helped

### Architecture & Documentation

Generated the eight architecture documents (`ARCHITECTURE.md`, `MODULE_STRUCTURE.md`, `CODING_CONVENTIONS.md`, `ERROR_HANDLING.md`, etc.) from initial conversations about the assignment requirements. These served as the project's "constitution" — Claude Code reads them at the start of every session via `CLAUDE.md`.

### Per-PR Specifications

Each PR has a detailed spec in `docs/prs/`. The spec is the most leveraged artifact in the workflow — when the spec is precise, Claude Code's output is precise.

Example: the PR 03 spec covered 3 stages and included full code skeletons for `WeatherRepositoryImpl`, `WeatherViewModel`, and `WeatherScreen`. Claude Code consumed this and produced the first working weather screen in a few hours, with one architectural bug found and fixed mid-PR (see "Notable Mistakes" below).

### Refactoring

Routine but tedious work — extracting hardcoded strings to `strings.xml` across 3 feature modules, refactoring 6 data sources from concrete classes to interfaces (TD-001 in PR 07) — was given to Claude Code with minimal supervision. These tasks are mechanically scoped; AI accelerates them with low risk.

### Bug Investigation

When emulator scenarios failed, Claude Code was asked to investigate before proposing a fix. Several bugs were diagnosed correctly without me having to describe the likely cause:

- PR 03: `WeatherViewModel` combine chain had no Error path when cache was empty AND refresh failed → screen stuck in Loading forever on cold-start offline
- PR 04: `selectedCityId` change triggered re-observe but not a fresh fetch → switching cities showed stale Loading for cities with no cache
- PR 05: `LaunchedEffect(permissionState.status)` fired on every re-composition after returning from CityList → spurious location re-resolution overwrote the user's manual city selection

### Test Generation

Repository, UseCase, and ViewModel tests in PR 07 were generated from patterns — the first test file (`WeatherRepositoryImplTest`) was reviewed carefully; subsequent files reused the same MockK + Turbine + `runTest` structure with minimal changes.

## Notable Mistakes (And What I Did About Them)

I include this section because AI is not infallible, and a candid review of where it fell short is more useful than a polished one-sided account.

### Mistake 1: Missing Error path in WeatherViewModel combine chain (PR 03)

**What happened**: The PR 03 spec designed `WeatherViewModel`'s `uiState` with a two-branch shape: `weather != null → Success`, `else → Loading`. There was no Error path for the case where Room has no cached data AND the network refresh has already failed. The spec — co-written by me with AI in web chat — simply didn't consider that branch.

**How it surfaced**: During Stage 3 emulator validation, Claude Code ran the cold-start offline scenario (airplane mode on, fresh install, no cache). The screen showed a Loading spinner and never moved. Claude Code cross-referenced `docs/ERROR_HANDLING.md`, which states explicitly: "Room has nothing and refresh failed → the UI must surface `UiState.Error(canRetry = true)`, not stay in Loading." That was the contradiction that proved the spec was wrong.

**How it was resolved**: Claude Code proposed adding `_lastRefreshError: MutableStateFlow<AppError?>` to the ViewModel and restructuring the combine into a three-way branch: `weather != null → Success`; `lastError != null → Error(canRetry=true)`; `else → Loading`. The `onRefresh` handler was extended to also transition from Error state. After the fix, the cold-start offline scenario passed: Error screen appeared with a Retry button.

**Lesson**: Even a detailed spec written collaboratively can miss edge cases that only become obvious at runtime. Having Claude Code reference the architecture docs during emulator runs — rather than just executing the spec — is what caught this. The spec documents are meant to be constraints, not just prompts; AI using them to challenge the spec's own gaps is more valuable than AI blindly following the spec.

### Mistake 2: Spec misjudgment about observe-equals-refresh in PR 04

**What happened**: The PR 04 spec (written with AI assistance in web chat) asserted that observing `selectedCityId` would "self-heal" the weather screen automatically — because `observeWeather` reads from Room, and Room emits whenever the selected city changes.

This was wrong. `observeWeather(cityId)` only reads existing rows — it doesn't trigger a network fetch. A freshly-selected city with no cached Room row would stay in Loading forever.

**How it surfaced**: Manual emulator test, scenario 6 — switch from Tokyo to London, watch the screen. Loading spinner, no data. Not a race condition; a design gap.

**How it was resolved**: Claude Code identified the problem and immediately proposed injecting `WeatherRepository` into `SelectCityUseCase` to trigger a refresh on city save. I rejected that — it would couple the city domain to the weather domain and force the same coupling on every future caller of `SelectCityUseCase`.

After discussion, we settled on adding `ObserveSelectedCityUseCase` and collecting it inside `WeatherViewModel.init`, triggering `refreshWeather()` on every city or unit change. "When to refresh" is a weather-domain concern; it belongs in `WeatherViewModel`, not in the city UseCase.

**Lesson**: AI's first proposal to fix a bug isn't always architecturally clean. The spec writer (me, with AI's help) can be wrong in the spec; pushing back on the fix led to a better solution than accepting the first one.

### Mistake 3: Stale city bug revealed by PR 05

**What happened**: PR 04 added `ObserveSelectedCityUseCase`, which called `getCityById(id)` — a one-shot Room query — to look up the selected city by its saved ID.

PR 05 added location auto-detect. The mechanism: detect coordinates → reverse-geocode → upsert a `City` row with `id = "current_location"` → `setSelectedCityId("current_location")`. On re-detection at a different location, the row's content changed (new name, new lat/lng) but the ID stayed `"current_location"`. Since `getCityById` was a one-shot call, it returned the previous value — the reactive chain didn't notice the row content had changed.

Result: after moving the emulator from Tokyo to Osaka, the app still showed Tokyo weather because `ObserveSelectedCityUseCase` was still holding a stale `City` object.

**How it surfaced**: User testing on the emulator with `Set Location`. The app showed the old location name after the new detection completed.

**How it was resolved**: Claude Code diagnosed the problem correctly. The fix was to switch from `getCityById(id)` (one-shot) to `combine(selectedCityId, cityRepository.observeSavedCities())` (reactive). Any Room row mutation now propagates through the chain automatically. TD-002 (logged in PR 04 with the prediction "if `selectedCityId` starts emitting at high frequency, promote to a Flow-based DAO query") was marked resolved.

**Lesson**: Logged technical debt is a real prediction mechanism. TD-002 went from "Low / re-evaluate in PR 05/06" to "user-facing bug" within one PR. The log made the diagnosis 5x faster because when the bug appeared, the root cause was already described.

## What AI Didn't Do (And Why)

- **AI didn't decide the architecture.** I chose Clean Architecture + 12 modules + Open-Meteo before the first AI conversation. AI documented and refined; it didn't generate from a blank prompt.
- **AI didn't write the assignment requirements.** All requirements came from the human-authored `docs/ASSIGNMENT.md`.
- **AI didn't run the emulator.** Every "this PR is done" required me sitting at the emulator, running through the listed scenarios. Several PRs failed this step on the first try (see above).
- **AI didn't manage Git.** Each PR was created, reviewed, and merged manually on GitHub. Branch hygiene was my responsibility.
- **AI didn't pick UX details** — color choices, icon sizes, copy text — without my direction. Visual decisions stayed human-driven.

### Treating spec as a draft, not a contract

Three of the seven PR specs had architectural mistakes I caught only during
implementation or emulator testing — and corrected before merge. The spec
isn't a contract with AI; it's a draft that gets revised when reality pushes
back. PR 04's spec was wrong about "observe = refresh." PR 05's spec didn't
anticipate a race between init refresh and permission re-resolve. Catching
these required treating my own spec critically, not just taking AI's interpretation.

### Living docs that AI reads

`CLAUDE.md` at repo root tells Claude Code:
- The architectural principles (non-negotiable)
- The current PR and its scope
- Where to find detailed conventions
- Specific rules learned over the project (e.g., "flag version changes before applying," "use Traditional Chinese for explanations")

This file evolved with the project. When Claude Code's behavior wasn't quite right, the fix was usually adding a rule to `CLAUDE.md`, not arguing with the model — and the new rule applied immediately and consistently across future sessions.

### TECH_DEBT.md as a prediction log

When AI proposed a workaround that wasn't ideal, I logged it as a TD entry rather than letting it disappear into the commit history. Each entry includes: what it is, why it's deferred, and which PR is expected to resolve it.

3 entries logged → 3 entries resolved. Logging tech debt isn't just bookkeeping — it actively accelerated diagnosis when those predictions came true.

### Per-stage checkpoints

Each PR had 2–3 stages. Claude Code stopped between stages and waited for approval. This gave me natural review points without getting buried in a single large diff. The alternative — "just implement everything" — would have made mistakes harder to catch and correct mid-PR.

### Retrospectives as source material

Each PR spec ends with a "Post-PR Retrospective" section. Filling it in after each merge created the first draft of this document — no separate note-taking required.

## Honest Self-Assessment

**Time saved by AI**: Significant on implementation (roughly 50–60%), much higher on documentation. Without AI, this project would have had fewer modules, less documentation, and fewer edge cases handled.

**Quality improvement from AI**: Mixed. Code patterns are more consistent than I'd produce alone, and the architecture documents are more thorough. But three of the project's notable bugs (Mistakes 1–3 above) involved AI proposing a flawed design that I initially trusted. Without the emulator verification step, at least two would have shipped.

**The real leverage**: The biggest productivity gain wasn't the code generation — it was the workflow: per-PR specs written before any code, stage checkpoints, `TECH_DEBT.md` as a prediction log, retrospectives as documentation drafts. These practices would be worth keeping even on a project with no AI involved.

This was the first time I worked with AI in this density across an entire
project. I went in expecting a code generator. I left with a collaborator
that occasionally needed correcting and a workflow I'll keep using.