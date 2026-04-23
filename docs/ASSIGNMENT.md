# Assignment Requirements

> Faithful transcription of the original home assignment PDF, plus a compliance checklist
> tracking how we address each requirement.
>
> **Source:** "The weather forecast app – ON Android homework assignment" (OpenNet).
> **Purpose of this document:** Single source of truth for what the reviewer is evaluating.
> When implementation decisions conflict, this document wins over our design docs.

---

## Overview

The purpose of the assignment is to assess:

- Object-oriented analysis and modelling skills
- Kotlin coding skills
- Android project structuring

Guidance from the brief:

> Take your time on the task, but don't get too carried away. If you submit a solution
> that is in any way incomplete, the parts that you decided to focus on are relevant.

> You are free to use any AI tools to assist you with this task — including for UI/UX design,
> icon creation, layout generation, or even code scaffolding. If you do use any tools,
> please include a short .md file describing which ones you used and how they helped.

> The focus of this assignment is on your understanding of product logic and implementation —
> not whether everything is handcrafted from scratch.

---

## Functional Requirements

The application must contain the following features:

1. Displaying the weather forecast for the **current day**.
2. Displaying the weather forecast for the **week**.
3. A **city list** where users can select a city to view its weather forecast information.

---

## Technical Requirements

### Tech stack (required)

- Kotlin
- Coroutines
- Jetpack Compose
- Clean Architecture approach

### Modularization (required)

- The solution must contain **at least one feature-module**.
- Reference: [Android modularization guide](https://developer.android.com/topic/modularization)

### API

- Any service can be used to get a forecast; free / limited / trial versions are allowed.
- Example mentioned in the brief: OpenWeatherMap API.

### Design

- Design decisions are entirely at the implementer's discretion.

---

## Delivery Requirements

- The solution needs to be **100% executable**.
- Provide a **link to the GitHub repository** where the solution is committed.
  - The repo must **not be private or restricted**.
- If AI tools are used, include a short `.md` file describing which ones were used and how they helped.

---

## How We Address Each Requirement

This section tracks our approach and completion status. Keep it honest and up-to-date.

### Functional requirements

| Requirement | Our approach | Status |
|-------------|--------------|--------|
| Current-day forecast | `:feature:weather` displays current temperature, feels-like, humidity, wind, condition | ⏳ PR 03 |
| Weekly forecast | 7-day list in the same screen, via Open-Meteo `daily` endpoint | ⏳ PR 03 |
| City list + selection | `:feature:citylist` with search (Open-Meteo geocoding), saved cities, tap-to-switch | ⏳ PR 04 |

### Technical requirements

| Requirement | Our approach | Status |
|-------------|--------------|--------|
| Kotlin | Sole implementation language | ⏳ All PRs |
| Coroutines | Used throughout; Flow for reactive streams; suspend for one-shot | ⏳ All PRs |
| Jetpack Compose | All UI in Compose; Material 3 | ⏳ PR 02–06 |
| Clean Architecture | Three-layer separation enforced by module boundaries (see `docs/ARCHITECTURE.md`) | ⏳ PR 02–03 |
| At least one feature module | We have two: `:feature:weather`, `:feature:citylist` | ⏳ PR 03–04 |

### API

| Choice | Rationale |
|--------|-----------|
| **Open-Meteo** (`api.open-meteo.com` + `geocoding-api.open-meteo.com`) | Free, no API key required. Reviewer can clone and run without setup friction. Details in `docs/TECH_DECISIONS.md`. |

### Delivery

| Requirement | Our approach | Status |
|-------------|--------------|--------|
| 100% executable | Verified in PR 07 DoD: fresh clone → `./gradlew build` → app runs. No API key setup needed. | ⏳ PR 07 |
| Public GitHub repo | Hosted under personal GitHub account | ⏳ Pre-delivery |
| AI usage document | `AI_USAGE.md` generated in PR 07, covering design discussions, document generation, and per-PR code generation | ⏳ PR 07 |

---

## Compliance Checklist (Pre-Delivery)

Run through this list before submitting. Every item must be checked.

### Functional
- [ ] App launches without crash on a fresh install
- [ ] Current-day weather is displayed (temperature, condition, additional details)
- [ ] Weekly (7-day) forecast is displayed
- [ ] City list is accessible
- [ ] Cities can be searched and added to the list
- [ ] Selected city's weather appears on the main screen
- [ ] Switching cities works and persists across restarts

### Technical
- [ ] Written in Kotlin (no Java)
- [ ] Coroutines used (no blocking IO on main thread)
- [ ] UI in Jetpack Compose (no XML layouts in app code)
- [ ] Clean Architecture: domain layer has no Android/network/database imports
- [ ] At least one feature module (we have two): `:feature:weather`, `:feature:citylist`

### Delivery
- [ ] Repo is **public** on GitHub (not private, not restricted, not organization-locked)
- [ ] Fresh clone + `./gradlew assembleDebug` succeeds on any machine with Android SDK
- [ ] No `local.properties` or secrets required — app runs out of the box
- [ ] `README.md` exists and includes: screenshots, how to run, architecture summary
- [ ] `AI_USAGE.md` exists at the repo root and is substantive
- [ ] All PRs merged into `main`
- [ ] `main` branch is the delivered state

---

## What Is NOT Required (Scope Boundaries)

Explicitly out of scope per the brief — **do not** spend effort on these:

- Automated UI testing (not mentioned in the brief; our project also excludes it per `docs/DEVELOPMENT_PLAN.md`)
- Backend server or any infrastructure beyond the free weather API
- Authentication / user accounts
- Premium features, subscriptions, in-app purchases
- Widgets, Wear OS, Android Auto surfaces
- Internationalization beyond the default locale (string resources are structured for future i18n but not translated)
- Push notifications
- Offline-first sync machinery beyond the simple SSOT with Room

---

## Grading Lens (What the Reviewer Likely Cares About)

Inferred from the brief's stated goals — not official grading criteria, but a useful filter for prioritization decisions:

| What they're evaluating | How we demonstrate it |
|-------------------------|------------------------|
| Object-oriented analysis and modelling | Clean domain model (`:core:model`), sealed error hierarchy (`AppError`), proper abstraction boundaries |
| Kotlin coding skills | Idiomatic Kotlin (sealed types, Flow, coroutines, extensions, `data class`, scope functions); no Java legacy patterns |
| Android project structuring | Multi-module structure, convention plugins, version catalog, dependency inversion, Hilt DI |
| Product logic and implementation | Real features that actually work end-to-end, not scaffolding without substance |
| Completeness vs. depth trade-off | If incomplete, the parts we chose to focus on should themselves be high quality |

---

## Conflict Resolution

If any other project document (`ARCHITECTURE.md`, `CODING_CONVENTIONS.md`, etc.) suggests an approach that conflicts with this document's requirements, **this document wins**. Update the other document to align.

If a requirement here is ambiguous, prefer the interpretation that produces the more visibly polished, working feature for the reviewer — not the one that's architecturally "purer" but less usable.
