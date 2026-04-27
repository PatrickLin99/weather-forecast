# PR 06 — Polish: Refresh, Units, Error UX, and Visual Refinement

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 06.
> **Execution mode: 2-stage with checkpoint between stages.**
>
> - Stage 1: Functional polish (pull-to-refresh, unit toggle, strings extraction)
> - Stage 2: Visual polish (error UX, TD-003 fix, CityList current_location distinction)
>
> Both stages end with a manual emulator pass; user approval required between them.

## Goal Recap

Bring the app from "functionally complete" to "deliverable quality." No new features—everything here refines what PR 03–05 already produces.

**End state after this PR:**
- Pull-to-refresh on weather screen actually fetches fresh data (not just plays animation)
- Unit toggle (°C / °F) in TopAppBar; selection persists; weather re-fetches with new unit
- All hardcoded user-facing strings moved into `strings.xml`
- `current_location` row in CityList visually distinct from manually-added cities (small icon)
- Error states have consistent treatment (Snackbar for transient, banner for persistent)
- Deprecated icons resolved (TD-003)
- No regressions in PR 03/04/05 scenarios

**What this PR does NOT include:**
- Tests (PR 07)
- README / AI_USAGE.md (PR 07)
- TD-001 data source refactor (PR 07)
- New features

## Prerequisites

- [ ] PR 05 merged to `main`
- [ ] Local `main` is up to date
- [ ] `./gradlew clean build` passes
- [ ] All 6 PR 05 emulator scenarios still pass (regression baseline)
- [ ] Branch created: `git checkout -b feat/06-polish`
- [ ] `CLAUDE.md` "Current PR" updated to PR 06
- [ ] Scan `docs/TECH_DEBT.md` for items targeting PR 06:
  - **TD-003** (HelpOutline AutoMirrored) — included in this PR
  - **TD-002** — already resolved in PR 05, no action
  - **TD-001** — still targeted at PR 07, no action

## Reference Documents (Must-Read)

1. **`docs/CODING_CONVENTIONS.md`** — strings.xml policy
2. **`docs/ERROR_HANDLING.md`** — UiState transitions and error UX patterns
3. **`docs/MODULE_STRUCTURE.md`** — `:core:designsystem` reusable components
4. **PR 05 retrospective** in `docs/prs/PR05_LOCATION.md` — the "Open UX question: CityList visual distinction for current_location" comment
5. **Material 3 PullToRefresh docs** — https://developer.android.com/jetpack/compose/state#pullrefresh
6. **PR 03/04/05 outputs** — `WeatherViewModel`, `WeatherScreen`, `CityListScreen`, `SavedCityItem`

---

# STAGE 1: Functional Polish

**Scope:**
- Pull-to-refresh on weather screen
- °C / °F toggle in WeatherScreen TopAppBar
- Extract all hardcoded strings into `strings.xml`

**Verification:** `./gradlew :app:installDebug` + manual emulator pass for refresh, unit toggle, locale check.

**Checkpoint:** Stop after this stage, wait for user approval.

## A. Pull-to-Refresh

### Approach

Use Material 3's `PullToRefreshBox` (stable since Compose Material 3 1.3+). Wraps existing scrollable content. Pull gesture triggers a callback; we map that to `WeatherViewModel.onRefresh()` (already exists from PR 03).

### File: `feature/weather/build.gradle.kts`

Verify `androidx.compose.material3` is on a version that includes `PullToRefreshBox`. Should be present from PR 02. No new deps expected.

### File: `WeatherScreen.kt` — modify `WeatherSuccessContent`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherSuccessContent(
    state: WeatherUiState.Success,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isStale && state.transientMessage != null) {
                StaleDataBanner(message = state.transientMessage)
            }
            CurrentWeatherHeader(weather = state.weather)
            WeatherDetailsRow(weather = state.weather)
            DailyForecastList(forecasts = state.weather.daily)
        }
    }
}
```

### Required ViewModel changes

Add `isRefreshing` signal:

```kotlin
private val _isRefreshing = MutableStateFlow(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

fun onRefresh() {
    val currentCity = (uiState.value as? WeatherUiState.Success)?.city ?: return
    viewModelScope.launch {
        refreshMutex.withLock {
            _isRefreshing.value = true
            try {
                refreshWeather(currentCity)
                    .onSuccess { _lastRefreshError.value = null }
                    .onFailure { _lastRefreshError.value = it }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
```

Pass through to Composable:

```kotlin
val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
// In WeatherContent → pass through to WeatherSuccessContent
```

**Critical detail — `_isRefreshing` reset in `finally`:** if `refreshWeather` throws or is cancelled, the spinner must stop. Don't put `_isRefreshing.value = false` only on success path.

**Critical detail — `refreshMutex` reuse:** PR 05 already added a `Mutex` for serializing init refresh and permission re-resolve. Reuse the same mutex here so manual pull-to-refresh doesn't race with auto-refresh from city/permission changes.

### Manual test (during Stage 1, not Stage 1's stop point)

- Pull weather screen down → spinner appears → completes within ~2s
- Pull during airplane mode → spinner shows briefly → spinner stops, transient error message appears
- Pull repeatedly very fast → no race, no double-fetch (Mutex serializes)

## B. °C / °F Unit Toggle

### Approach

Toggle button in TopAppBar (between title and "Cities" button). Tapping flips `temperatureUnit` in DataStore. PR 03's `ObserveSelectedCityWeatherUseCase` already combines `temperatureUnit` Flow into the chain — when it changes, weather flow re-emits with the new unit.

**But** there's a subtlety: changing the unit doesn't re-fetch. Open-Meteo returns the unit you ask for at request time; cached weather has the old unit baked in. We need to **trigger a refresh** when unit changes, similar to how PR 05 triggers refresh on city change.

### Domain extension

`UserPreferencesRepository` already has `setTemperatureUnit` from PR 02. No new code in domain.

### `WeatherViewModel.kt` extension

The `init` block currently collects `observeSelectedCity()` to drive refresh. Extend it to also react to unit changes:

```kotlin
init {
    viewModelScope.launch {
        resolveInitialCity(useLocation = _hasLocationPermission.value)

        // Combine selected-city changes AND unit changes — refresh on either.
        combine(
            observeSelectedCity(),
            userPreferencesRepository.temperatureUnit,
        ) { city, unit -> city to unit }
            .distinctUntilChanged()
            .collect { (city, _) ->
                refresh(city)  // existing helper using Mutex
            }
    }
}
```

**Why combine and not separate collects:**
- Separate collects would race (city change AND unit change at the same moment → two refresh calls)
- `combine` emits once per upstream change, Mutex inside `refresh` serializes
- `distinctUntilChanged` filters duplicate (same city, same unit) — though equality semantics here are by `Pair`, which compares both components

**Note on `_unit`:** `RefreshWeatherUseCase` reads the current unit via `userPreferencesRepository.temperatureUnit.first()` (from PR 03). It will pick up the new value automatically. No parameter change needed.

**Inject `UserPreferencesRepository` into ViewModel** if not already present. Check existing constructor.

### `WeatherScreen.kt` — TopAppBar update

Add a `IconButton` with `WbSunny` / `Thermostat` icon (or text "°C" / "°F" for clarity). Tapping calls a new ViewModel method.

```kotlin
// In WeatherViewModel
fun onToggleTemperatureUnit() {
    viewModelScope.launch {
        val current = userPreferencesRepository.temperatureUnit.first()
        val next = if (current == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT
                   else TemperatureUnit.CELSIUS
        userPreferencesRepository.setTemperatureUnit(next)
    }
}
```

```kotlin
// In WeatherScreen / WeatherContent topBar actions
TopAppBar(
    title = { /* same */ },
    actions = {
        // Unit toggle
        TextButton(onClick = onToggleUnit) {
            Text(
                text = when (currentUnit) {
                    TemperatureUnit.CELSIUS -> "°C"
                    TemperatureUnit.FAHRENHEIT -> "°F"
                },
            )
        }
        // Existing Cities button
        TextButton(onClick = onNavigateToCityList) {
            Text(stringResource(R.string.weather_action_cities))
        }
    },
)
```

For this you'll need the current unit visible to the Composable. Two options:

**Option B1**: Add `currentUnit: TemperatureUnit` to `WeatherUiState.Success`. Cleaner since `Weather` already has `unit` — actually, `state.weather.unit` is already there for Success state. **Use that.**

**Option B2**: Expose a separate `StateFlow<TemperatureUnit>` from ViewModel. More plumbing.

**Decision: B1.** Read `state.weather.unit` when state is Success. For Loading/Error states, default to CELSIUS in the toggle display (or hide the toggle in those states — your call, document the choice).

**Recommendation:** Hide the toggle in Loading/Error. It's a no-op there anyway — there's no weather to re-display.

### Manual test

- App in CELSIUS, tap "°C" → flips to "°F" → numbers refresh after ~1-2s with Fahrenheit values
- Tap again → back to °C
- Force-stop, relaunch → unit preference persisted (last selected unit shown)
- Toggle during pull-to-refresh → no race, both refreshes complete in order
- Toggle while offline → unit changes immediately in display? **No** — refresh fails, UI keeps old cached weather. The transient message UX (PR 03) handles this. Unit will switch on next successful fetch.

**Important UX note on the offline case:** Document this behavior in retrospective. The "old unit values stay until next successful fetch" might confuse a user. Acceptable for delivery, but note the limitation.

## C. Strings.xml extraction

### Audit

Find all `Text("...")` and `placeholder = { Text("...") }` and similar hardcoded strings in `:feature:weather`, `:feature:citylist`, and `:core:designsystem`.

```bash
grep -rn 'Text\("\|stringResource\|placeholder' feature/ core/designsystem/ | grep -v "stringResource" | head -40
```

Move each to the appropriate module's `res/values/strings.xml`.

### Module-by-module

**`feature/weather/src/main/res/values/strings.xml`** (create):
```xml
<resources>
    <string name="weather_title_default">Weather</string>
    <string name="weather_action_cities">Cities</string>
    <string name="weather_message_unable_to_load">Unable to load weather</string>
    <string name="weather_action_retry">Retry</string>
    <string name="weather_label_feels_like">Feels like</string>
    <string name="weather_label_humidity">Humidity</string>
    <string name="weather_label_wind">Wind</string>
    <string name="weather_label_seven_day">7-Day Forecast</string>
    <string name="weather_unit_celsius">°C</string>
    <string name="weather_unit_fahrenheit">°F</string>
    <string name="weather_banner_location_permission">Enable location for auto-detect</string>
    <string name="weather_banner_location_disabled">Location is off — tap to enable in Settings</string>
    <!-- error messages -->
    <string name="error_no_network">No internet connection</string>
    <string name="error_network_timeout">Request timed out</string>
    <string name="error_server">Server error. Please try again later.</string>
    <string name="error_unknown_network">Network error</string>
    <string name="error_database">Local data error</string>
    <string name="error_data_parsing">Unexpected data format</string>
    <string name="error_unexpected">Something went wrong</string>
</resources>
```

**`feature/citylist/src/main/res/values/strings.xml`** (create):
```xml
<resources>
    <string name="citylist_title">Cities</string>
    <string name="citylist_search_placeholder">Search cities</string>
    <string name="citylist_search_no_matches">No matches</string>
    <string name="citylist_search_failed">Search failed. Try again.</string>
    <string name="citylist_empty_hint">Search above to add a city</string>
    <string name="citylist_action_clear">Clear</string>
    <string name="citylist_action_back">Back</string>
    <string name="citylist_action_delete">Delete</string>
    <string name="citylist_label_selected">Selected</string>
    <string name="citylist_label_current_location">Current location</string>
</resources>
```

**`core/designsystem/src/main/res/values/strings.xml`** (create):
```xml
<resources>
    <string name="designsystem_action_retry">Retry</string>
    <string name="designsystem_label_unknown_condition">Unknown</string>
</resources>
```

(Or namespace strings under feature module since designsystem rarely has user-facing text — your call.)

### Update Composables

Replace `Text("Cities")` → `Text(stringResource(R.string.citylist_title))` etc.

In `WeatherViewModel.toUserMessage()`, the user-facing strings need access to a Context to resolve `R.string.error_X`. **Don't inject Context into ViewModel.** Instead:

**Approach**: Map `AppError` to `Int` (a string resource id), then resolve in the Composable.

```kotlin
// In WeatherUiState.Success
data class Success(
    val weather: Weather,
    val city: City,
    val isStale: Boolean = false,
    val transientMessageRes: Int? = null,  // R.string.error_X, null = no message
)
```

Or expose `AppError` directly and let the Composable do the mapping:

```kotlin
data class Success(
    val weather: Weather,
    val city: City,
    val isStale: Boolean = false,
    val transientError: AppError? = null,
)

// In Composable
@Composable
private fun AppError.toUserMessage(): String = when (this) {
    AppError.NoNetwork -> stringResource(R.string.error_no_network)
    AppError.NetworkTimeout -> stringResource(R.string.error_network_timeout)
    is AppError.ServerError -> stringResource(R.string.error_server)
    AppError.UnknownNetworkError -> stringResource(R.string.error_unknown_network)
    AppError.DatabaseError -> stringResource(R.string.error_database)
    is AppError.DataParsingError -> stringResource(R.string.error_data_parsing)
    is AppError.Unexpected -> stringResource(R.string.error_unexpected)
    else -> stringResource(R.string.error_unexpected)
}
```

**Recommendation: second approach (AppError → @Composable mapper).** Keeps ViewModel platform-free and pushes the `R` reference into the layer that already has Context.

### Verification

```bash
# Should find no remaining bare Text("...") with English content in feature modules
grep -rn 'Text("[A-Z][a-z]' feature/ core/designsystem/

# Acceptable hits: emoji, icons, "$value°", "${city.name}" interpolation — review by hand
```

## Stage 1 Verification

```bash
./gradlew clean build
./gradlew :app:installDebug
```

### Manual emulator pass for Stage 1

1. Pull-to-refresh on Taipei weather → spinner → fresh data
2. Switch to °F → values update with Fahrenheit numbers in 1-2s
3. Force-stop, relaunch → still °F
4. Switch back to °C → values update
5. Trigger pull-to-refresh during airplane mode → spinner stops, error message appears (existing transient pattern)
6. Run **all PR 03 / 04 / 05 happy-path scenarios** to confirm no regression: weather load, city switch, location detect

## Stage 1 Commits

```
feat: add pull-to-refresh to weather screen
feat: add temperature unit toggle in weather TopAppBar
refactor: route weather refresh through unit-aware combine in ViewModel
refactor: extract user-facing strings to strings.xml
refactor: map AppError to user message in Composable layer
```

5 commits, ~one per concern. Don't squash.

### STOP HERE

Report Stage 1 results. Wait for user approval before Stage 2.

---

# STAGE 2: Visual Polish

**Scope:**
- Resolve TD-003 (HelpOutline AutoMirrored)
- CityList: visually distinguish `current_location` row from manual cities
- Error UX consistency review (Snackbar for transient vs. banner for persistent)

**Verification:** `./gradlew :app:installDebug` + manual emulator pass.

## A. Resolve TD-003

### File: `core/designsystem/component/WeatherIcon.kt`

```kotlin
import androidx.compose.material.icons.automirrored.filled.HelpOutline

WeatherCondition.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
```

That's it. One-line fix.

### Update `docs/TECH_DEBT.md`

Move TD-003 from `## Open Items` to `## Resolved Items`:

```markdown
## Resolved Items

### TD-002: ... (already resolved in PR 05) ...

### TD-003: HelpOutline replaced with AutoMirrored variant

**Resolved in**: PR 06 (Stage 2)
**Resolution date**: <today's date>

Replaced `Icons.Filled.HelpOutline` with `Icons.AutoMirrored.Filled.HelpOutline` in `core/designsystem/component/WeatherIcon.kt`. Build no longer emits the deprecation warning. RTL locales now render the icon correctly mirrored.
```

Update Index table — change TD-003 status to Resolved.

## B. CityList: visualize `current_location` distinction

### Context

PR 05's retrospective flagged the open UX question: `current_location` rows appear in CityList alongside manually-added cities, looking identical. User can't tell at a glance which one is auto-detected.

### Approach

Modify `SavedCityItem` to show a small location icon prefix when `city.isCurrentLocation == true`. Keep selection check mark behavior orthogonal.

### File: `feature/citylist/component/SavedCityItem.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedCityItem(
    city: City,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onTap() },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (city.isCurrentLocation) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.citylist_label_current_location),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(city.name)
            }
        },
        supportingContent = { Text(city.country) },
        leadingContent = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.citylist_label_selected),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.citylist_action_delete),
                )
            }
        },
    )
}
```

**Why icon next to the name, not as leading icon**:
- Leading icon slot is already used for the selected check mark
- Putting current_location indicator inline next to the name creates a clear "this name is auto-detected" association
- Small (16dp) and primary-tinted to be unobtrusive

**Alternative considered**: change the entire row's background tint. Rejected — too visually heavy for "this is auto-detected" information that should be informational, not stylistic.

### Update existing strings if needed

`citylist_label_current_location` already added in Stage 1. If it wasn't, add now.

## C. Error UX consistency review

### Audit current state

Open the app and trigger each error condition:

| Trigger | Current UX (after PR 05) | Verdict |
|---------|---|---|
| Cold-start offline (no cache, no network) | Full-screen ErrorState with Retry | ✅ keep |
| Refresh fails with cache present | Stale banner inline at top of weather | ✅ keep |
| Search request fails | ErrorState fills the lower half of CityList | ⚠️ heavy for transient |
| Geocoder reverse fails (unlikely path) | Silent — falls back to default | ✅ keep (banner via permission state already prompts) |
| Pull-to-refresh fails | Stale banner, same as auto-refresh fail | ✅ keep |
| Unit toggle while offline | Old cache stays, no explicit error UX | ⚠️ minor |

### Changes

**Search failure** (CityListScreen): currently uses full ErrorState. Replace with an inline message above the saved cities list:

```kotlin
is SearchState.Error -> Column {
    SearchErrorBanner(
        message = stringResource(R.string.citylist_search_failed),
        onDismiss = { onQueryChanged("") },  // dismiss = clear search
    )
    SavedCitiesList(
        cities = uiState.savedCities,
        selectedCityId = uiState.selectedCityId,
        onTap = onCityTapped,
        onDelete = onDeleteCity,
    )
}
```

`SearchErrorBanner` is a new small component:

```kotlin
@Composable
internal fun SearchErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.citylist_action_clear))
            }
        }
    }
}
```

**Rationale**: search failure is transient (next keystroke triggers retry). User shouldn't lose access to their saved cities while a single failed search is on screen.

**Unit toggle while offline**: low priority. Add a brief Snackbar via `SnackbarHostState` if there's time:

```kotlin
// In WeatherViewModel, when unit changes and refresh fails:
private val _snackbarMessage = MutableSharedFlow<String>()
val snackbarMessage = _snackbarMessage.asSharedFlow()

// In refresh failure path:
_snackbarMessage.emit("Showing previous values — couldn't update")
```

Compose side uses `SnackbarHost` + `LaunchedEffect` collecting `snackbarMessage`. Implement only if Stage 2 has time; otherwise note in retrospective.

## Stage 2 Verification

```bash
./gradlew clean build
./gradlew :app:installDebug

# Verify no remaining HelpOutline deprecation warning
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | grep -i "deprecated"
# Should not mention HelpOutline
```

### Manual emulator pass for Stage 2

1. Force `WeatherCondition.UNKNOWN` (e.g., a city with weird weather code) → help icon renders, no crash
2. CityList → `current_location` row has small location icon next to name
3. CityList → manually-added city row has NO location icon
4. CityList → search "zzzqqq" while offline → search error banner shows above saved list (saved list still visible and tappable)
5. All Stage 1 + PR 03/04/05 scenarios still pass

## Stage 2 Commits

```
fix: resolve TD-003 — replace deprecated HelpOutline with AutoMirrored
docs: mark TD-003 resolved in TECH_DEBT
feat: add MyLocation icon to current_location rows in CityList
refactor: replace full-screen search error with inline banner
docs: mark PR06 done and add retrospective
```

---

## Final PR Verification

```bash
./gradlew clean build
./gradlew :app:installDebug

# Confirm no deprecation warnings (other than ones we know about)
./gradlew clean assembleDebug 2>&1 | grep -i "deprecat" | head

# Run boundary checks (just in case)
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore\|import com.example.weatherforecast.core.location\|import com.example.weatherforecast.core.data" feature/
# Should return nothing
```

## Expected total commits

8–12 across the 2 stages, plus 1–2 docs commits.

## Post-PR Retrospective

**Status**: Done — 2026-04-27

**Pull-to-refresh**: Worked without issues once screen pixel coordinates were correct for the 1280×2856 emulator. `PullToRefreshBox` + `rememberPullToRefreshState()` integrated cleanly; `_isRefreshing` in `finally {}` block guaranteed the spinner always clears. No interaction issues with the existing Mutex-guarded `refresh()` path from PR 05.

**Unit toggle**: Functioned correctly — toggling °C→°F triggered a real API call with `temperature_unit=fahrenheit`, values updated in ~1s. Persistence via DataStore confirmed across force-stop. Offline case: old cached values stay until next successful fetch; no explicit error UX for unit toggle failure (noted in the spec as acceptable). Deferred the optional Snackbar — it would require a `SharedFlow<String>` in ViewModel and a `SnackbarHost` in the screen; not worth the complexity for an edge case.

**String extraction**: Found no forgotten hardcoded strings from PR 03/04/05 — the audit was clean. `AppError → @Composable` mapper pattern (exposing `AppError` from ViewModel, resolving string in Composable) kept the ViewModel platform-free and was straightforward to implement.

**TD-003**: One-line import swap. No cascade — only `WeatherIcon.kt` referenced `HelpOutline`. Deprecation warning gone after the change.

**Search error UX change**: Replacing full-screen `ErrorState` with `SearchErrorBanner` above saved cities was the right call — users retain access to their city list when a search fails (e.g., offline). The inline `Surface(errorContainer)` banner is unobtrusive. The "Clear" button calls `onQueryChanged("")` which resets state back to Idle.

**CityList current_location distinction**: `MyLocation` icon (16dp, primary tint) inline next to the city name is subtle and clear. Using the `headlineContent` slot rather than `leadingContent` keeps the checkmark slot available for the selection indicator.

**New tech debt**: None introduced this PR. TD-001 remains open for PR 07.

**Snackbar for unit-toggle-offline**: Deferred. Noted in this retrospective. Not worth a TD entry — if it becomes a complaint, it's a minor enhancement.

---

## Claude CLI: How to Work on This PR

1. **Read this entire spec + the 5 reference docs before writing any code.**

2. **Execute in 2 stages.** After Stage 1, run the manual emulator pass yourself (don't ask user to test until Stage 2 done). Stop before Stage 2 only if Stage 1 has problems.

3. **Within a stage, autonomous execution is fine.** Don't ask "should I proceed" between sub-steps.

4. **Commit frequently but sensibly.** 4-6 commits per stage.

5. **If a build failure blocks progress for more than 2 attempts:** stop and describe.

6. **Do not:**
   - Add tests beyond trivial ones (PR 07)
   - Add features not in this spec
   - Refactor `:core:domain` or `:core:data` interfaces
   - Touch TD-001 (PR 07)
   - Change the architecture (PR 06 is polish, not redesign)

7. **Version changes:** Per CLAUDE.md rule 8, flag any `libs.versions.toml` upgrade request. Adding new entries is fine without flagging.

8. **String resource decisions:**
   - User-facing text → strings.xml in the relevant feature module
   - Icon `contentDescription` → strings.xml (accessibility matters even for internal builds)
   - Format strings (e.g., `"$value°"`) → keep as Composable-level interpolation; only extract literal text
   - Don't move the package name, manifest fields, or build-time configs

9. **AppError → user message mapping:** Mapping happens in Composable layer (uses `stringResource`), not in ViewModel. ViewModel exposes `AppError`, Composable resolves the string.

10. **If something seems to need a feature module to know about another feature module's strings:** that's a smell. Move shared strings to `:core:designsystem` or document why the duplication is acceptable.

11. **Visual decisions** (icon placement, color choices) — defer to spec; if the spec is silent, default to Material 3 conventions and small/subtle (not heavy/dramatic) treatments.

12. **Snackbar implementation in Stage 2 part C** is optional. If implementing, do it cleanly; if not, note in retrospective why deferred.
