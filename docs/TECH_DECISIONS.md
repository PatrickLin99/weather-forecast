# Technical Decisions

This document records the key technical decisions made for this project.
Each entry follows the **Architecture Decision Record (ADR)** format: context,
options considered, decision, rationale, and accepted trade-offs.

Decisions are ordered by topic, not chronology.

---

## 1. Weather API: Open-Meteo

**Context**

The app needs a weather API for current conditions, daily forecasts, and city geocoding. The assignment suggests OpenWeatherMap but permits any service.

**Options considered**

| Option | Pros | Cons |
|--------|------|------|
| OpenWeatherMap | Industry standard; rich data | Requires API key; free tier has rate limits; key management adds friction for reviewers |
| Open-Meteo | No API key required; free for non-commercial; current + daily + hourly in one call; built-in geocoding endpoint; unit conversion via query param | Less "enterprise-known"; smaller ecosystem |
| WeatherAPI.com | Good free tier | Requires API key |

**Decision:** Open-Meteo.

**Rationale**

- **Zero-friction review.** Reviewer can clone and run the repo without needing to register for a service or receive a key. This materially improves the submission experience.
- **Clean domain modeling.** A single endpoint returns current + daily + hourly, which reduces the number of API abstractions we need to build.
- **Built-in unit conversion.** Passing `temperature_unit=fahrenheit` as a query parameter means we don't build client-side conversion logic — the server handles it.
- **Separate geocoding endpoint.** City search works cleanly without mixing with forecast calls.

**Trade-offs accepted**

- Less well-known service. Mitigated by clean abstraction: swapping to OpenWeatherMap later would only touch `:core:network` and `:core:data`.
- No server-side API key means we skip a useful learning exercise around secret management. Not relevant to this assignment's scope.

---

## 2. Dependency Injection: Hilt

**Context**

The project requires a DI solution to wire Repositories, UseCases, and framework dependencies across multiple modules.

**Options considered**

| Option | Notes |
|--------|-------|
| Hilt | Google's recommended DI for Android; built on Dagger; compile-time DI |
| Koin | Kotlin-friendly; runtime DI; simpler learning curve |
| Manual DI | No framework; explicit wiring via constructor |

**Decision:** Hilt.

**Rationale**

- **Google's official recommendation** for Android. The assignment requires following Google's guidelines.
- **Compile-time safety.** Missing bindings fail the build rather than crashing at runtime.
- **First-class support** for ViewModels via `@HiltViewModel` and for Navigation Compose via `hilt-navigation-compose`.
- **Scales cleanly** across multi-module architecture — each module contributes its own `@Module`.

**Trade-offs accepted**

- Annotation processing (KSP) adds a small build-time cost.
- Slightly more ceremony than Koin for simple cases.
- `:core:domain` must be an Android Library (not a pure JVM library) because Hilt annotations require the Android toolchain. We judge this to be a reasonable trade since it removes a boundary but preserves the architectural intent.

---

## 3. Serialization: kotlinx.serialization

**Context**

DTOs from Open-Meteo must be deserialized into Kotlin data classes.

**Options considered**

| Option | Notes |
|--------|-------|
| kotlinx.serialization | Kotlin-native; compile-time code generation via compiler plugin; no reflection |
| Moshi | Mature; widely used; supports codegen |
| Gson | Simple; reflection-based; no longer actively maintained |

**Decision:** kotlinx.serialization.

**Rationale**

- **Kotlin-first design.** Handles sealed classes, default values, and `data object` cleanly.
- **No reflection at runtime** with the compiler plugin — better performance and R8 compatibility.
- **Aligned with Kotlin's evolution** — maintained by JetBrains.
- **Works with Navigation Compose's type-safe routes**, which require `@Serializable` on route objects.

**Trade-offs accepted**

- Requires applying the compiler plugin to each module that uses it.
- Slightly less tooling ecosystem than Moshi.

---

## 4. Persistence: Room + DataStore Preferences

**Context**

The app needs persistent storage for two distinct concerns: structured weather/city data, and user preferences.

**Options considered**

For structured data:
- Room (Google-recommended SQLite wrapper)
- SQLDelight (multiplatform-friendly)
- Raw SQLite

For settings:
- DataStore Preferences (key-value)
- DataStore Proto (typed schema)
- SharedPreferences (legacy)

**Decision:** Room for weather/city data, DataStore Preferences for settings.

**Rationale**

- **Room** is Google's official solution and integrates cleanly with Flow, making SSOT patterns natural.
- **DataStore Preferences** fits our settings needs (selected city id, temperature unit) without the schema ceremony of DataStore Proto.
- **Two distinct tools for two distinct concerns** is clearer than trying to force one tool for both.
- **SharedPreferences** is formally superseded by DataStore and should not be used in new projects.

**Trade-offs accepted**

- DataStore Proto would give stronger typing for settings but the two keys we store don't justify a `.proto` file and code generation.
- We maintain two persistence mechanisms; in a larger app this could be unified, but at our scale the split is cleaner.

---

## 5. Data flow: Single Source of Truth (Room)

**Context**

When the app has both a network source and a local cache, the UI can observe either. This creates a decision about which is authoritative.

**Options considered**

| Option | Behavior |
|--------|----------|
| Network-only | UI reads from network each time. No offline support. |
| Room-only | UI reads from Room; network only updates Room. |
| UI observes both | Combine flows from network and Room in ViewModel. |

**Decision:** Room is the Single Source of Truth. UI only observes Room; network is a side effect that updates Room.

**Rationale**

- **Offline support is free.** If the network fails, Room already has the last-successful data and the UI continues to work.
- **Consistency.** The UI sees one truth. There's no possibility of the network response arriving while a cached value is also displayed.
- **Testability.** ViewModel tests inject a fake `Repository` that drives a Room-shaped Flow — no network mocking needed.
- **Pull-to-refresh is a one-liner.** Calling `refreshWeather()` triggers the network → Room pipeline; the observing Flow updates the UI automatically.

**Trade-offs accepted**

- Room writes on every network success add a small IO cost. Negligible for the data volumes involved.
- First launch shows a brief empty state until the network call populates Room. Acceptable and handled in UiState with a Loading state.

---

## 6. Navigation: Callback injection (not Navigator pattern)

**Context**

Two feature modules need to navigate to each other, but feature modules should not depend on each other.

**Options considered**

| Option | Mechanism |
|--------|-----------|
| Callback injection | Each feature exposes `NavGraphBuilder.xxxScreen(onNavigateToY: () -> Unit)`. `:app` wires callbacks. |
| Navigator interface | Define `Navigator` in a shared module; features inject and call it. Implementation manipulates `NavController`. |
| Deep link strings | Features navigate by raw strings shared via constants. |

**Decision:** Callback injection.

**Rationale**

- **Keeps `NavController` confined to `:app`.** It's a UI-layer object and shouldn't escape into ViewModels or the DI graph.
- **Feature modules have zero knowledge of each other.** Not even an interface — purely callbacks.
- **This is the [Now in Android](https://github.com/android/nowinandroid) convention**, Google's official modularization reference.
- **Type-safe routes** via Navigation Compose 2.8+ work naturally with this pattern.

**Trade-offs accepted**

- For very large apps (20+ features, complex nav graphs), a Navigator abstraction can reduce `:app`'s size. At 2 features, callback injection is clearly simpler.
- Cross-feature coordination (e.g., "select a city, then update weather screen") cannot use direct calls. We solve this via shared DataStore Flow — which is a cleaner decoupling anyway.

---

## 7. Cross-feature coordination: Shared DataStore Flow

**Context**

When the user selects a city in `:feature:citylist`, `:feature:weather` must react and display that city's weather. The features cannot call each other directly (see decision #6).

**Options considered**

| Option | Mechanism |
|--------|-----------|
| Shared DataStore Flow | `:feature:citylist` writes `selectedCityId` → DataStore. `:feature:weather` observes the same Flow. |
| Navigation result passing | Return value from CityListScreen via `SavedStateHandle`. |
| Event bus | Shared `MutableSharedFlow` in a singleton. |

**Decision:** Shared DataStore Flow via `UserPreferencesRepository`.

**Rationale**

- **Persistence is free.** The selection survives process death and app restart.
- **Decoupled.** Features don't share any in-memory state; they communicate through a persisted source of truth.
- **Reactive.** `WeatherViewModel` observes the Flow and automatically re-queries weather when the city changes, without any manual wiring.
- **Testable.** Fake `UserPreferencesRepository` drives the Flow in tests.

**Trade-offs accepted**

- A write to disk on every city selection. Negligible at interactive speeds.
- Slight indirection compared to passing a value directly. This is the intentional decoupling we want.

---

## 8. UseCase policy: Add only when value-adding

**Context**

Clean Architecture traditions often advocate for wrapping every Repository method in a UseCase. This can lead to many one-line pass-through classes.

**Options considered**

| Option | Result |
|--------|--------|
| UseCase for every operation | Uniform; lots of boilerplate; many empty abstractions |
| UseCase only when they add value | Fewer classes; ViewModels sometimes call Repository directly |
| No UseCases | Simpler; loses the ability to orchestrate across Repositories |

**Decision:** UseCases are created only when they compose, transform, or orchestrate beyond a single Repository call.

**Rationale**

- **Google's [updated architecture guide](https://developer.android.com/topic/architecture#domain-layer)** explicitly states the domain layer is optional and should only hold logic that warrants it.
- **Avoids ceremony without benefit.** A UseCase like `class GetWeatherUseCase { operator fun invoke(id) = repo.getWeather(id) }` is pure overhead.
- **Reserves UseCases for real logic**: `ResolveInitialCityUseCase` has a 3-step fallback chain; `ObserveSelectedCityWeatherUseCase` composes two Flows with `flatMapLatest`. These genuinely benefit from extraction.

**Trade-offs accepted**

- Inconsistency: some ViewModel methods call UseCases, some call Repositories directly. We accept this in exchange for less noise.
- Developers must decide, case by case, whether a UseCase is warranted. This is documented in `docs/CODING_CONVENTIONS.md`.

---

## 9. Error modeling: Custom `Result<T, AppError>`

**Context**

The project must handle network errors, location errors, and database errors without crashing, and surface appropriate UX for each case.

**Options considered**

| Option | Notes |
|--------|-------|
| Custom `Result<T, E>` with sealed `AppError` | Explicit error types; type-safe error handling |
| Kotlin's built-in `kotlin.Result<T>` | Only wraps `Throwable`; limitations on public APIs |
| Throw exceptions from suspend functions | Idiomatic Kotlin but easy to miss errors |
| `Either<L, R>` from Arrow | Functional; adds a library dependency |

**Decision:** Custom `Result<T, AppError>` with `AppError` as a sealed class in `:core:common`.

**Rationale**

- **Type-safe error handling.** The compiler forces consumers to deal with errors explicitly.
- **`AppError` as a sealed class** models every error category in the app (`NoNetwork`, `LocationPermissionDenied`, `CityNotFound`, etc.) and enables exhaustive `when` branches.
- **Kotlin's `kotlin.Result`** has historical public-API restrictions and forces errors to be `Throwable` subtypes, which is a weaker contract.
- **Arrow** is a mature library but overkill for this scope.

**Trade-offs accepted**

- We maintain our own small `Result` implementation (roughly 40 lines).
- Interop with Kotlin's `runCatching` needs an adapter at the boundary (inside `apiCall { }`).

Full `AppError` hierarchy: see `docs/ERROR_HANDLING.md`.

---

## 10. Build configuration: Version Catalog + Convention Plugins

**Context**

With 12 modules, dependency declarations and common build configuration would be duplicated heavily without centralization.

**Options considered**

| Option | Notes |
|--------|-------|
| Version Catalog + Convention Plugins | Single source for versions; shared build logic in reusable plugins |
| Version Catalog only | Centralizes versions, but each module still declares compile targets, Compose setup, etc. |
| `buildSrc` | Older Gradle pattern; causes full-project recompilation on any change |
| Ad-hoc (per-module configuration) | Simple at first; fragile at scale |

**Decision:** `gradle/libs.versions.toml` (Version Catalog) combined with convention plugins in `build-logic/`.

**Rationale**

- **Now in Android uses this exact setup** — it's the current Google-recommended pattern.
- **Dependency upgrades touch one file** (`libs.versions.toml`).
- **Module `build.gradle.kts` files become trivial** — typically 3–5 lines of plugin + 4–6 lines of dependencies.
- **Convention plugins scale.** Adding a new feature module means `alias(libs.plugins.weatherapp.android.feature)` — one line.
- **`buildSrc`** triggers full-project rebuilds when edited. Convention plugins don't have this problem.

**Trade-offs accepted**

- Convention plugins require a separate `build-logic/` Gradle project with its own Kotlin DSL setup. Initial learning curve, but amortized across 12 modules.

---

## 11. Concurrency: Coroutines + Flow (no RxJava)

**Context**

The tech stack specifies Coroutines. The team's experience includes RxJava, which is common in legacy Android projects.

**Decision:** Coroutines + Flow exclusively. No RxJava.

**Rationale**

- **Assignment requirement.**
- **Modern Android-standard.** Google's libraries (Room, DataStore, Lifecycle, Navigation) all have first-class coroutine support.
- **Compose integration.** `collectAsStateWithLifecycle` bridges Flow → Compose naturally.
- **No JVM-Rx interop cost.** A single reactive paradigm throughout.

**Trade-offs accepted:** none material for this project.

---

## 12. Image loading: Coil

**Context**

Weather condition icons may be bundled in the app, but if we add an online icon source or user-selected location photos later, an image-loading library is needed.

**Options considered**

| Option | Notes |
|--------|-------|
| Coil | Kotlin-first; coroutine-based; Compose integration via `AsyncImage` |
| Glide | Mature; Java-style API; heavier |
| Picasso | Simple; less actively maintained |

**Decision:** Coil.

**Rationale**

- **Kotlin-first.** Uses coroutines and Flow internally.
- **Dedicated Compose artifact** (`coil-compose`) with an idiomatic `AsyncImage` Composable.
- **Lightweight** compared to Glide.

**Trade-offs accepted:** none. Coil is the de facto choice for new Compose apps.

---

## 13. Testing: JUnit4 + MockK + Turbine

**Context**

The project requires unit tests for Repository, UseCase, and ViewModel layers.

**Decision:**

- **JUnit4** as the test runner
- **MockK** for mocking
- **Turbine** for Flow assertions
- **kotlinx-coroutines-test** for coroutine control
- **Google Truth** (optional) for fluent assertions

**Rationale**

- **JUnit4** remains the Android standard; JUnit5 support on Android still has friction.
- **MockK** is Kotlin-first and handles `final` classes and coroutines without extra configuration (unlike Mockito).
- **Turbine** reduces Flow testing from dozens of lines to a few — essential for testing reactive code.
- **No UI tests.** Out of scope; explicitly excluded for this assignment.

**Trade-offs accepted**

- JUnit4 is older than JUnit5, but Android tooling compatibility outweighs the newer features.

---

## 14. Minimum SDK: API 26

**Context**

Setting `minSdk` is a trade-off between device coverage and available platform APIs.

**Options considered**

| minSdk | Device coverage | Notes |
|--------|-----------------|-------|
| 21 (Lollipop) | ~100% | Forces many compat workarounds |
| 24 (Nougat) | ~99% | Java 8 APIs available |
| 26 (Oreo) | ~97% | Full `java.time`, full `Optional`, better vector drawables |
| 29 (Android 10) | ~90% | Scoped storage; cleaner but narrower |

**Decision:** `minSdk = 29`.

**Rationale**

- **Matches Android Studio's default** for new Empty Activity (Compose) projects as of our project initialization. Accepting the IDE default removes one piece of project setup friction.
- **`java.time` is native** on API 26+, so we're well past that threshold without needing library desugaring.
- **Scoped storage** is available from API 29, so any future file I/O follows the modern model by default.
- **Still covers ~90% of active devices** — plenty for a demo app.
- **Geocoder's async API** arrived at API 33, but we still support pre-33 via Dispatchers.IO wrapping — 29 is not the constraint there.

**Trade-offs accepted**

- Devices running Android 5.0–9.0 (API 21–28) are not supported. Acceptable for this demo's scope.
- We give up roughly 7% extra device coverage (~97% at minSdk 26 vs ~90% at 29) in exchange for fewer compat considerations.

---

## Decision Changelog

| Date | Decision | Change |
|------|----------|--------|
| Initial | All above | Recorded during project setup |
| 2026-04-23 | #14 minSdk | Revised from 26 to 29 during PR 01 scaffolding. Aligns with the Android Studio default that the project was bootstrapped from; trade-offs re-recorded above. |

> Future decisions and revisions are appended here with rationale.
