# PR 08 — Post-Delivery Hotfix: 3 Bugs from PR 07 Final Regression

> Companion to PR 07's final-regression retrospective.
> **Execution mode: single-stage with three independent fix commits.**
>
> Three independent bugs introduced by PRs 03/04/05 and surfaced by PR 07's
> 21-scenario emulator regression. They share no common code path; each fix
> is self-contained.

## Goal Recap

Resolve three user-facing bugs that escaped earlier PRs' scenario coverage:

1. **Bug A**: Default city ID collision — `DefaultCity.TAIPEI.id` doesn't match Open-Meteo geocoding's id for Taipei. Searching Taipei creates a duplicate row alongside the default-installed one. The default row is undeletable because `DeleteCityUseCase` re-inserts it.
2. **Bug B**: Location re-resolve doesn't fire on cold start when permission was already granted and the device location changed since last launch.
3. **Bug C**: Permission banner becomes a silent no-op after the user denies twice (Android system behavior). User perceives banner as broken.

**End state after this PR:**
- Searching Taipei merges with the default-installed row (no duplicate)
- Cold start with granted permission detects current location every time
- Permission banner falls through to system Settings when `launchPermissionRequest` would be a no-op

**What this PR does NOT include:**
- Migration logic for users with `selectedCityId = "default_taipei"` from older versions (delivery target is fresh install)
- Banner copy refinements beyond the bare minimum
- Network connectivity listener (acceptable limitation, future enhancement)
- New tests (the existing 36 tests stay green; UI fixes don't have unit-testable seams)

## Prerequisites

- [ ] PR 07 merged to `main`
- [ ] Local `main` is up to date
- [ ] `./gradlew clean build test` passes
- [ ] Branch created: `git checkout -b fix/08-post-delivery-bugs`
- [ ] `CLAUDE.md` "Current PR" updated to PR 08

## Reference Documents

1. PR 07 retrospective in `docs/prs/PR07_TESTS_AND_DELIVERY.md` — original bug descriptions
2. PR 05 spec — `LocationPermissionBanner`, `WeatherViewModel.onLocationPermissionChanged`, the `wasUsable / nowUsable` transition logic
3. PR 03 spec — `DefaultCity`, `ResolveInitialCityUseCase`
4. Android docs:
   - https://developer.android.com/training/permissions/requesting#explain — "Don't ask again" behavior

---

# Fix 1: Default City ID Collision (Bug A)

## Diagnosis

`DefaultCity.TAIPEI.id` is the hardcoded string `"default_taipei"`.
`GeocodingResultDto.toDomain()` produces `id = id.toString()` (Open-Meteo's numeric id stringified).

These two ids are different. Both rows can coexist in the `cities` table. The user sees both in CityList; deleting the `"default_taipei"` row triggers `DeleteCityUseCase` fallback, which re-inserts it via `cityRepository.saveCity(DefaultCity.TAIPEI)`.

## Step 1: Get Open-Meteo's real id for Taipei

```bash
curl 'https://geocoding-api.open-meteo.com/v1/search?name=Taipei&count=1&language=en'
```

Look for `"id": <number>` in the response. As of writing, Taipei is `1668341` (this is a GeoNames id; stable).

Record the value. Verify it's correct by also checking:

```bash
curl 'https://geocoding-api.open-meteo.com/v1/search?name=Taipei&count=5&language=en' | grep -A 3 '"name":"Taipei"'
```

Make sure you pick **the city** of Taipei (admin level matches Taiwan's capital). A district or neighborhood with the same name could appear in `count=5`.

## Step 2: Update `DefaultCity.kt`

**File**: `core/common/src/main/kotlin/com/example/weatherforecast/core/common/constant/DefaultCity.kt`

**Before:**

```kotlin
object DefaultCity {
    val TAIPEI = City(
        id = "default_taipei",
        name = "Taipei",
        country = "Taiwan",
        latitude = 25.0330,
        longitude = 121.5654,
        isCurrentLocation = false,
    )
}
```

**After:**

```kotlin
object DefaultCity {
    /**
     * The city used as initial selection on fresh install and as fallback when
     * deletion would leave selectedCityId orphaned.
     *
     * The id matches Open-Meteo Geocoding's id for Taipei so that searching
     * for Taipei via the API merges with this row instead of creating a
     * duplicate. The id is stable (GeoNames-backed).
     */
    val TAIPEI = City(
        id = "1668341",  // Open-Meteo Geocoding id for Taipei, Taiwan
        name = "Taipei",
        country = "Taiwan",
        latitude = 25.0330,
        longitude = 121.5654,
        isCurrentLocation = false,
    )
}
```

## Step 3: Migration consideration (skip)

Existing installs may have `selectedCityId = "default_taipei"` in DataStore and a row with that id in Room. **For this delivery, we do not handle migration** — reviewer is a fresh install. Document this in retrospective.

If you wanted migration, it would belong in `ResolveInitialCityUseCase`:
- After reading `selectedCityId`, if it equals `"default_taipei"` → rewrite to `DefaultCity.TAIPEI.id`, save the new DefaultCity row, delete the old one
- This is roughly 8 lines of code, ~10 minutes of work, but adds complexity for a non-target case

Skip for now.

## Step 4: Verify

After the change, run on a clean emulator:

```
1. Clear app data (adb shell pm clear com.example.weatherforecast)
2. Launch app → shows Taipei (DefaultCity)
3. Open CityList → see exactly ONE Taipei row
4. Search "Taipei" → API returns Taipei (id=1668341)
5. Tap the result
6. Open CityList → still exactly ONE Taipei row (Upsert merged)
```

If step 6 still shows two Taipei rows, the id you picked from the API doesn't match what `GeocodingResultDto.toDomain()` produces. Verify by adding a temporary log in `CityRemoteDataSourceImpl` to print the id of the search result.

## Step 5: Update tests

Search for any test that hardcodes `"default_taipei"`:

```bash
grep -rn '"default_taipei"' core/ feature/ app/
```

Likely targets:
- `ResolveInitialCityUseCaseTest`
- `DeleteCityUseCaseTest`
- Any test factory function with `mockCity(id = "default_taipei")`

Update each to use `DefaultCity.TAIPEI.id` (constant reference) rather than the literal. This is a small refactor that makes the tests robust against future id changes.

## Commit

```
fix: align DefaultCity.TAIPEI.id with Open-Meteo Geocoding id (Bug A)

DefaultCity.TAIPEI used a hardcoded "default_taipei" id while
GeocodingResultDto.toDomain() produces Open-Meteo's numeric id
(stringified). Searching Taipei created a duplicate row alongside
the default-installed one; the default row was undeletable because
DeleteCityUseCase re-inserts it as fallback.

Fix: use Open-Meteo's id (1668341, GeoNames-backed, stable) for
DefaultCity.TAIPEI. Search results for Taipei now Upsert into the
same row.

Migration not implemented — delivery target is fresh install.
Existing installs may need to clear app data.
```

---

# Fix 2: Permission Re-Resolve Trigger (Bug B)

## Diagnosis

`WeatherViewModel.onLocationPermissionChanged` only triggers re-resolve on the `wasUsable=false → nowUsable=true` transition (PR 05's Bug B fix). This was correct for preventing re-composition spam, but it has a side effect:

When the app cold-starts with permission already granted **and** the device location has changed since last detection, the transition guard never fires (permission state didn't transition), so location is never re-resolved. The cached `current_location` row stays, and weather shows the previously-detected city.

## Goal

On every cold start, if permission is granted, run location re-resolution **once**. After that, fall back to the existing transition-only behavior.

## Approach

Add a `hasReceivedFirstPermissionState` flag to `WeatherViewModel`. The first time `onLocationPermissionChanged` is called after init, force a re-resolve if `nowUsable`. Subsequent calls use the existing transition guard.

## Step 1: Update `WeatherViewModel.kt`

**File**: `feature/weather/src/main/kotlin/com/example/weatherforecast/feature/weather/WeatherViewModel.kt`

**Before** (current PR 05 logic):

```kotlin
fun onLocationPermissionChanged(granted: Boolean, locationEnabled: Boolean) {
    val wasUsable = _hasLocationPermission.value && _isLocationEnabled.value
    _hasLocationPermission.value = granted
    _isLocationEnabled.value = locationEnabled
    val nowUsable = granted && locationEnabled
    if (nowUsable && !wasUsable) {
        viewModelScope.launch {
            resolveInitialCity(useLocation = true)
        }
    }
}
```

**After**:

```kotlin
private var hasReceivedFirstPermissionState = false

fun onLocationPermissionChanged(granted: Boolean, locationEnabled: Boolean) {
    val wasUsable = _hasLocationPermission.value && _isLocationEnabled.value
    _hasLocationPermission.value = granted
    _isLocationEnabled.value = locationEnabled
    val nowUsable = granted && locationEnabled

    // First permission state received: always re-resolve if usable.
    // This handles the cold-start case where permission is already granted
    // and the device location may have changed since last launch.
    val isFirstReceived = !hasReceivedFirstPermissionState
    hasReceivedFirstPermissionState = true

    val shouldReResolve = nowUsable && (isFirstReceived || !wasUsable)
    if (shouldReResolve) {
        viewModelScope.launch {
            resolveInitialCity(useLocation = true)
        }
    }
}
```

**Why this works:**

- **Cold start, permission granted, same location as before**: first call → `isFirstReceived = true` → re-resolve. Re-detect returns the same `current_location` row (with `id = "current_location"` upsert), no visible change to user. Cost: one extra location fetch on cold start. Acceptable.
- **Cold start, permission granted, location changed**: first call → `isFirstReceived = true` → re-resolve. Re-detect returns new coordinates → reverse-geocode → upsert `current_location` row with new name/lat/lng. **Bug B fixed.**
- **Recomposition (e.g., return from CityList) without permission state change**: `isFirstReceived = false`, `wasUsable = nowUsable = true` → guard prevents re-resolve. **PR 05's Bug B fix preserved.**
- **Permission revoked then re-granted**: `wasUsable = false → nowUsable = true` → re-resolve (existing transition logic). Still works.

## Step 2: No changes to `WeatherScreen.kt`

The Composable's `LaunchedEffect(permissionState.status)` already calls `onLocationPermissionChanged` on first composition. No change needed there.

## Step 3: Verify

```
Pre-condition: permission granted in a previous session.

Test 1 — same location:
1. Set emulator location to Taipei (25.0330, 121.5654)
2. Launch app → expect Taipei weather
3. Force-stop, relaunch → expect Taipei weather (no change)

Test 2 — location changed:
1. Set emulator location to Tainan (22.99, 120.21)
2. Force-stop, relaunch
3. Expect: Tainan weather appears within ~2-5 seconds (re-detect + re-geocode)
4. Open CityList → expect ONE current_location row labeled Tainan, NOT both Taipei and Tainan
```

If Test 2 step 3 still shows the old city, the re-resolve isn't firing. Check that `hasReceivedFirstPermissionState` is correctly persisted within the ViewModel lifecycle (it should reset on every ViewModel creation = every cold start).

## Step 4: Test update (optional)

If `WeatherViewModelTest` has a test for `onLocationPermissionChanged`, add a case for the first-receive path:

```kotlin
@Test
fun `first permission state received triggers re-resolve when nowUsable`() = runTest {
    // setup mocks
    val viewModel = WeatherViewModel(/* ... */)
    
    viewModel.onLocationPermissionChanged(granted = true, locationEnabled = true)
    
    // verify resolveInitialCity(useLocation = true) was called
    coVerify { resolveInitialCity(useLocation = true) }
}
```

If `WeatherViewModelTest` doesn't currently cover `onLocationPermissionChanged`, skip — adding a new test path here means setting up the entire ViewModel mock graph just for this; not worth in the bug-fix PR.

## Commit

```
fix: re-resolve location on cold start when permission already granted (Bug B)

WeatherViewModel.onLocationPermissionChanged only re-resolved on the
wasUsable=false → nowUsable=true transition. This prevented spam from
recompositions but missed the case where permission was already granted
across cold starts and the device location had changed in between —
location was never re-resolved, so the cached current_location city
showed instead of the new one.

Fix: track hasReceivedFirstPermissionState. The first permission state
received after ViewModel creation always triggers re-resolve if usable;
subsequent calls retain the existing transition-only guard.
```

---

# Fix 3: Permission Banner Falls Through to Settings (Bug C)

## Diagnosis

Android's `launchPermissionRequest()` becomes a silent no-op after the user denies twice (or once on devices that treat first deny as "Don't ask again"). The system permission dialog will not appear, regardless of how many times the user taps it.

Accompanist's `PermissionStatus` exposes this as `Denied(shouldShowRationale = false)` — meaning the dialog won't show. Tapping the banner in this state does nothing visible.

## Goal

When the banner is tapped and `launchPermissionRequest()` would be a no-op, fall through to the system's app settings screen, where the user can manually grant permission.

## Step 1: Update `WeatherScreen.kt`

**File**: `feature/weather/src/main/kotlin/com/example/weatherforecast/feature/weather/WeatherScreen.kt`

Locate the `LocationPermissionBanner(onTap = ...)` call. Change `onTap` to a lambda that branches based on permission status:

**Before:**

```kotlin
LocationPromptState.NeedsPermission -> LocationPermissionBanner(
    onTap = { permissionState.launchPermissionRequest() },
)
```

**After:**

```kotlin
LocationPromptState.NeedsPermission -> LocationPermissionBanner(
    onTap = {
        val status = permissionState.status
        if (status is PermissionStatus.Denied && !status.shouldShowRationale) {
            // launchPermissionRequest() would be a silent no-op.
            // Fall through to system Settings.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        } else {
            permissionState.launchPermissionRequest()
        }
    },
)
```

Required imports (add if missing):

```kotlin
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.accompanist.permissions.PermissionStatus
```

## Step 2: (Optional) Banner copy update

Banner currently says "Enable location for auto-detect" regardless of state. Optional: change copy when `shouldShowRationale = false` to "Open Settings to enable location".

This requires plumbing the state into `LocationPermissionBanner`. Roughly 5 lines of change:

```kotlin
@Composable
internal fun LocationPermissionBanner(
    onTap: () -> Unit,
    needsSettingsRedirect: Boolean = false,  // NEW
    modifier: Modifier = Modifier,
) {
    // ...
    Text(
        text = if (needsSettingsRedirect) {
            stringResource(R.string.weather_banner_open_settings)
        } else {
            stringResource(R.string.weather_banner_location_permission)
        },
        // ...
    )
}
```

And new string resource:

```xml
<string name="weather_banner_open_settings">Open Settings to enable location</string>
```

**Recommendation**: include this. The user-perceived "broken banner" issue is partly the silent no-op, but also the unchanged copy that doesn't tell the user where to go. Both fixes together make the UX coherent.

## Step 3: Verify

```
Test 1 — first deny (rationale would show):
1. Clear app data
2. Launch → banner visible
3. Tap banner → system permission dialog appears
4. Tap "Don't allow"
5. Tap banner again → system permission dialog appears AGAIN (PermissionStatus.Denied with shouldShowRationale=true)
6. Tap "Don't allow" again
7. Tap banner → expect system Settings (App info) page to open

Test 2 — direct Settings redirect (already denied twice):
1. From above state, return to app
2. Banner visible (and if you implemented copy change: "Open Settings to enable location")
3. Tap banner → expect Settings opens immediately
4. Manually grant permission in Settings
5. Return to app → banner disappears within 1-2 seconds, current location detected

Test 3 — fresh grant after Settings:
1. After Test 2 step 5, weather should switch to detected city
2. CityList shows current_location row only once
```

## Commit

```
fix: permission banner falls through to system Settings after deny (Bug C)

After the user denies the permission dialog twice (or once on devices
that treat first deny as "Don't ask again"), Android's
launchPermissionRequest() becomes a silent no-op. The banner appeared
broken to the user — tapping it did nothing visible.

Fix: when permissionState.status is Denied(shouldShowRationale=false),
launch ACTION_APPLICATION_DETAILS_SETTINGS instead. Banner copy also
updates to "Open Settings to enable location" in this state for clarity.
```

---

# Final Verification

After all 3 fixes are committed:

```bash
./gradlew clean build test
./gradlew :app:installDebug
./gradlew :app:assembleRelease
```

All 36 tests should still pass. If any fail, it's likely a hardcoded `"default_taipei"` string in tests (Fix 1 step 5).

## Re-run the 3 originally-failing scenarios

| Bug | Scenario | Expected Result |
|---|---|---|
| A | Search Taipei after fresh install | Single Taipei row in CityList |
| B | Set emulator to Tainan, force-stop, relaunch | Tainan weather appears |
| C | Deny permission twice, tap banner | Settings opens |

Plus a quick smoke test of the 18 previously-passing scenarios from PR 07 — no regressions.

## Update TECH_DEBT.md

Add to Resolved Items:

```markdown
### TD-XXX (placeholder — assign next available number)

**Resolved**: PR 08, 2026-04-XX

**Bugs fixed:**
- Default city id collision (Bug A) — DefaultCity.TAIPEI.id changed to Open-Meteo's geocoding id
- Permission re-resolve on cold start (Bug B) — added hasReceivedFirstPermissionState flag in WeatherViewModel
- Banner ineffective after deny (Bug C) — falls through to system Settings when launchPermissionRequest is a no-op

**Why these escaped earlier PRs:**

PR 03/04/05 each had scenario lists, but none required:
- Searching for a city that already exists under a different id (Bug A)
- Cold-starting with granted permission AND a changed device location since last launch (Bug B)
- Re-tapping a denied banner after Android marked the request as silently rejected (Bug C)

The bugs aren't AI-induced — they're from human-authored scenario lists that didn't anticipate these flows. Final regression in PR 07 caught them.
```

## Update CLAUDE.md

Mark PR 08 as done.

## Update DEVELOPMENT_PLAN.md

If DEVELOPMENT_PLAN.md only lists PR 01–07, add a brief PR 08 entry as a hotfix not in the original plan.

# Post-PR Retrospective (fill after merge)

- All 3 fixes worked on first emulator run? Or surprises?
- Any test had to be updated due to DefaultCity id change?
- Any unforeseen interaction between Fix 2 and PR 05's existing transition guard?
- Anything that should be a new TD entry going forward?

---

## Claude CLI: How to Work on This PR

1. Read this entire spec before any code change.

2. Execute the 3 fixes **in the order listed** (A, B, C). They're independent, but A's test updates depend on the new DefaultCity id constant, so doing A first avoids retouching tests later.

3. **One commit per fix**, using the commit messages provided. Don't combine.

4. Verify each fix on the emulator before moving to the next. If Bug A's verification fails, don't start Bug B — root-cause Bug A first.

5. Don't add features. Don't refactor unrelated code. Don't bump dependency versions.

6. **Don't implement migration logic for `"default_taipei"`** — delivery target is fresh install.

7. After all 3 fixes pass, propose your retrospective text and stop. The user will review before merging.

## Known limitation discovered, not fixed

During verification of Bug B's fix, found that **rapid repeated navigation**
(Taipei → back → Taipei → back, 10+ cycles) causes the WeatherScreen to render
white. ViewModel state remains correct (uiState stays in Success);
WeatherSuccessContent recomposition still fires; but the rendered output is blank.

**Investigated paths that did NOT resolve it:**
- Changed `SharingStarted.WhileSubscribed(5_000)` to `Lazily` — no effect
- Replaced `WeatherSuccessContent` body with a minimal Box(Color.Cyan) — still white
- Replaced `Box(fillMaxSize())` with `Box(weight(1f).fillMaxWidth())` — still white

**Suspected cause** (not confirmed): layout-state accumulation in Compose under
rapid navigation cycles, possibly involving NavBackStackEntry-scoped
ViewModel + remember state interaction.

**Why deferred:**
- Reproduction requires deliberate rapid back-and-forth navigation
  (10+ cycles); not encountered in normal use
- Root cause requires deeper investigation with Layout Inspector
- Fix scope is unknown — could be a Compose Material 3 / NavHost bug
  outside this codebase

**Logged as TD-005 for follow-up.**

### Other refinements during verification

While running through the regression scenarios, the WeatherScreen TopAppBar
title's clickable behavior was removed. Two redundant navigation entries to
CityList (title and button) created ambiguity; the explicit "Cities" button
is now the single entry. Cleaner UX, simpler code, fewer hidden affordances.
