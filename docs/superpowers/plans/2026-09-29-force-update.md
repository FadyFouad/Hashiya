# Force Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Both apps read a minimum supported build from a JSON file on GitHub Pages at launch and on each return to the foreground. A lower build shows a full-screen "Update required" screen. First release: 0.1.0 (build 1).

**Architecture:** Each platform gets three layers, mirroring the arXiv client:
- A small network client that fetches and decodes the file.
- A repository that applies the rule and turns every failure into "no block".
- A UI-state holder that stays blocked once blocked.

The blocking screen lives in each design system and reuses the existing empty-state component. Each app's root view swaps it in for the whole UI.

**Tech Stack:**
- Android: Kotlin, OkHttp, kotlinx.serialization, Hilt, Jetpack Compose + Material 3, Robolectric, Roborazzi.
- iOS: Swift 6, URLSession, SwiftUI, Observation, Swift Testing, swift-snapshot-testing.

**Spec:** `docs/superpowers/specs/2026-09-29-force-update-design.md`

## Global Constraints

- Config URL: `https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json`.
- JSON keys: `android.minimumVersionCode`, `android.storeUrl`, `ios.minimumBuild`, `ios.storeUrl`. Unknown keys are ignored.
- Rule: blocked only when `current build < minimum` and the store link is an `https://` URL. Any failure or missing field means no block.
- Once blocked, stay blocked for the life of the process. A later failed or unblocked check never unblocks.
- The request sends no API key, no identifiers and no query string. Timeout: 5 seconds.
- Version: `versionName = "0.1.0"`, `versionCode = 1` (Android); `MARKETING_VERSION: "0.1.0"`, `CURRENT_PROJECT_VERSION: "1"` (iOS).
- Strings, exactly:
  - Title: "Update required" / "يلزم التحديث"
  - Message: "This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device." / "لم يعد هذا الإصدار من حاشية مدعومًا. حدّث التطبيق لمتابعة استخدامه، وستبقى أوراقك المحفوظة على جهازك."
  - Button: "Update" / "تحديث"
- Commits are authored `Fady <fady.fouad.a@gmail.com>`, with no AI attribution of any kind (CLAUDE.md).
- Screenshot and snapshot baselines are recorded on CI only. Never commit locally recorded baseline images.

## Review Focus

- **Offline or GitHub down with the minimum raised:** the app opens normally. Pinned by the repository "throws" tests in Tasks 3 and 7.
- **A slow config server:** the app shows normally while the check runs, and a return to the foreground mid-check doesn't start a second request. Pinned by the held-check tests in Task 5.
- **A typo in the store link** (`http://`, empty, not a URL): no block, instead of a dead Update button. Pinned in Tasks 3 and 7.
- **A minimum written wrongly in JSON** (`"2"` or `2.5`), or an iOS build number that isn't a whole number: no block. Pinned in Tasks 2, 6 and 7.
- **Blocked, then a later check fails or reports nothing:** still blocked, and no new request. Pinned in Tasks 5 and 7.

## File Structure

**Android**
- Create `core/model/src/main/kotlin/com/etatech/hashiya/core/model/RequiredUpdate.kt`: the value both layers pass around.
- Create `core/network/src/main/java/com/etatech/hashiya/core/network/AppConfigDataSource.kt`: fetch and decode.
- Modify `core/network/src/main/java/com/etatech/hashiya/core/network/ArxivDataSource.kt`: `awaitBody` becomes `internal` for reuse.
- Modify `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`: provide the data source.
- Create `core/data/src/main/java/com/etatech/hashiya/core/data/repository/AppUpdateRepository.kt`: the interface and `ConfigAppUpdateRepository`.
- Modify `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`: bind it.
- Create `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeAppUpdateRepository.kt`.
- Create `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/UpdateRequiredScreen.kt`, plus the strings and icon.
- Create `app/src/main/java/com/etatech/hashiya/update/AppUpdateViewModel.kt` and `app/src/main/java/com/etatech/hashiya/update/AppUpdateModule.kt`.
- Modify `MainActivity.kt`, `navigation/HashiyaApp.kt` and `app/build.gradle.kts`.

**iOS**
- Create `ios/HashiyaKit/Sources/HashiyaModel/RequiredUpdate.swift`.
- Create `ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift`.
- Create `ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift` (repository and `AppUpdateModel`).
- Create `ios/HashiyaKit/Sources/HashiyaTesting/FakeAppUpdateRepository.swift`.
- Create `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/UpdateRequiredView.swift`, plus strings.
- Modify `ios/Hashiya/AppContainer.swift`, `ios/Hashiya/RootView.swift`, `ios/Hashiya/UITestingStubs.swift` and `ios/project.yml`.

**Outside this repo:** `app-config.json` and the privacy policy's `index.html` in `FadyFouad/Hashiya-Privacy-Policy`.

---

### Task 1: Version 0.1.0 (build 1)

**Files:**
- Modify: `app/build.gradle.kts` (`versionName`)
- Modify: `ios/project.yml` (`MARKETING_VERSION`)
- Modify: `docs/release.md`

**Interfaces:** Produces the version numbers that every later build reports.

- [ ] **Step 1: Change the versions**

In `app/build.gradle.kts`, set:
```kotlin
        versionCode = 1
        versionName = "0.1.0"
```
In `ios/project.yml`, under `settings.base`:
```yaml
    MARKETING_VERSION: "0.1.0"
    CURRENT_PROJECT_VERSION: "1"
```

- [ ] **Step 2: Update the runbook's version references**

In `docs/release.md`, replace "Written for 1.0" with "Written for 0.1.0", and "Version 1.0 → English (U.S.) and Arabic" with "Version 0.1.0 → English (U.S.) and Arabic". Change the example tag to `git tag v0.1.0 && git push origin v0.1.0`. Then run `grep -nE '\b1\.0\b' docs/release.md` and confirm no remaining match refers to the app's version.

- [ ] **Step 3: Verify both builds report 0.1.0 (1)**

Run:
```bash
./gradlew :app:processDebugMainManifest -q && grep -o 'versionName="[^"]*"\|versionCode="[^"]*"' app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
cd ios && xcodegen generate -q && xcodebuild -project Hashiya.xcodeproj -scheme Hashiya -showBuildSettings 2>/dev/null | grep -E " (MARKETING_VERSION|CURRENT_PROJECT_VERSION) ="
```
Expected: `versionCode="1"`, `versionName="0.1.0"`, `MARKETING_VERSION = 0.1.0` and `CURRENT_PROJECT_VERSION = 1`.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts ios/project.yml docs/release.md
git commit -m "chore: set the first release to version 0.1.0 (build 1)"
```

---

### Task 2: Android config data source (`core:network`)

**Files:**
- Create: `core/network/src/main/java/com/etatech/hashiya/core/network/AppConfigDataSource.kt`
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/ArxivDataSource.kt` (`private suspend fun Call.awaitBody()` → `internal`)
- Modify: `core/network/src/main/java/com/etatech/hashiya/core/network/di/NetworkModule.kt`
- Test: `core/network/src/test/java/com/etatech/hashiya/core/network/AppConfigDataSourceTest.kt`

**Interfaces:**
- Produces `interface AppConfigDataSource { suspend fun androidConfig(): NetworkPlatformConfig? }`. It throws `NetworkException` on transport, HTTP or parse failure.
- Produces `@Serializable data class NetworkPlatformConfig(val minimumVersionCode: Long? = null, val storeUrl: String? = null)`.
- Produces `internal const val APP_CONFIG_URL`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.etatech.hashiya.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AppConfigDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): AppConfigDataSource =
        OkHttpAppConfigDataSource(buildAppConfigOkHttpClient(), server.url("/Hashiya-Privacy-Policy/app-config.json"))

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun failureOf(block: suspend () -> Unit): NetworkFailure {
        try {
            block()
        } catch (e: NetworkException) {
            return e.failure
        }
        fail("Expected NetworkException")
        error("unreachable")
    }

    @Test
    fun readsTheAndroidEntry() = runTest {
        enqueue(200, SAMPLE)

        assertEquals(NetworkPlatformConfig(minimumVersionCode = 3, storeUrl = PLAY_URL), dataSource().androidConfig())
    }

    @Test
    fun requestsTheFileWithNoQueryOrKey() = runTest {
        enqueue(200, SAMPLE)
        dataSource().androidConfig()

        val request = server.takeRequest()
        assertEquals("/Hashiya-Privacy-Policy/app-config.json", request.url.encodedPath)
        assertNull(request.url.query)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun ignoresUnknownKeys() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":2,"storeUrl":"$PLAY_URL","message":"x"},"web":{}}""")

        assertEquals(NetworkPlatformConfig(2, PLAY_URL), dataSource().androidConfig())
    }

    @Test
    fun aMissingAndroidEntryIsNull() = runTest {
        enqueue(200, """{"ios":{"minimumBuild":5}}""")

        assertNull(dataSource().androidConfig())
    }

    @Test
    fun aMinimumWrittenAsTextIsMalformed() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":"2","storeUrl":"$PLAY_URL"}}""")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun aFractionalMinimumIsMalformed() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":2.5,"storeUrl":"$PLAY_URL"}}""")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun notJsonIsMalformed() = runTest {
        enqueue(200, "<html>Not found</html>")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun aMissingFileIsAnHttpFailure() = runTest {
        enqueue(404, "Not found")

        assertEquals(NetworkFailure.Http(code = 404, usedUserKey = false), failureOf { dataSource().androidConfig() })
    }

    @Test
    fun anUnreachableServerIsConnectivity() = runTest {
        server.close()

        assertEquals(NetworkFailure.Connectivity, failureOf { dataSource().androidConfig() })
    }

    private companion object {
        const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
        val SAMPLE = """
            {
              "android": { "minimumVersionCode": 3, "storeUrl": "$PLAY_URL" },
              "ios": { "minimumBuild": 1, "storeUrl": "https://apps.apple.com/app/id0000000000" }
            }
        """.trimIndent()
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :core:network:testDebugUnitTest --tests "*AppConfigDataSourceTest" -q`
Expected: compilation fails with `Unresolved reference 'OkHttpAppConfigDataSource'`.

- [ ] **Step 3: Implement**

In `ArxivDataSource.kt`, change `private suspend fun Call.awaitBody(): String` to `internal suspend fun Call.awaitBody(): String`, so the new source reuses its cancellation and error mapping.

Create `AppConfigDataSource.kt`:
```kotlin
package com.etatech.hashiya.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val APP_CONFIG_URL = "https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json"

/** The app's remote config on GitHub Pages: the minimum supported build. Sends no key and nothing about the user. */
interface AppConfigDataSource {
    /**
     * The Android entry, or null when the file has none.
     * @throws NetworkException when the file can't be fetched, answers with an error, or can't be read.
     */
    suspend fun androidConfig(): NetworkPlatformConfig?
}

@Serializable
data class NetworkPlatformConfig(val minimumVersionCode: Long? = null, val storeUrl: String? = null)

@Serializable
internal data class NetworkAppConfig(val android: NetworkPlatformConfig? = null)

/** A plain client with short timeouts, so a slow GitHub never holds the check for long. */
internal fun buildAppConfigOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

internal class OkHttpAppConfigDataSource(private val client: OkHttpClient, private val url: HttpUrl) : AppConfigDataSource {
    override suspend fun androidConfig(): NetworkPlatformConfig? {
        val body = client.newCall(Request.Builder().url(url).build()).awaitBody()
        return try {
            OpenAlexJson.decodeFromString<NetworkAppConfig>(body).android
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw NetworkException(NetworkFailure.MalformedResponse, e)
        }
    }
}
```

In `NetworkModule.kt`, add the imports `com.etatech.hashiya.core.network.APP_CONFIG_URL`, `com.etatech.hashiya.core.network.AppConfigDataSource`, `com.etatech.hashiya.core.network.OkHttpAppConfigDataSource` and `com.etatech.hashiya.core.network.buildAppConfigOkHttpClient`. Then add to `NetworkModule`:
```kotlin
    @Provides
    @Singleton
    fun provideAppConfigDataSource(): AppConfigDataSource =
        OkHttpAppConfigDataSource(buildAppConfigOkHttpClient(), APP_CONFIG_URL.toHttpUrl())
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :core:network:testDebugUnitTest -q && ./gradlew spotlessCheck -q`
Expected: BUILD SUCCESSFUL. All network tests pass, including the arXiv tests that use `awaitBody`.

`OpenAlexJson` has `coerceInputValues = true`. If `aMinimumWrittenAsTextIsMalformed` passes with a coerced value instead of failing, decode with a dedicated `Json { ignoreUnknownKeys = true }` in this file instead of `OpenAlexJson`, and rerun.

- [ ] **Step 5: Commit**

```bash
git add core/network
git commit -m "feat: fetch the app config from GitHub Pages on Android"
```

---

### Task 3: Android repository and model (`core:model`, `core:data`, `core:testing`)

**Files:**
- Create: `core/model/src/main/kotlin/com/etatech/hashiya/core/model/RequiredUpdate.kt`
- Create: `core/data/src/main/java/com/etatech/hashiya/core/data/repository/AppUpdateRepository.kt`
- Modify: `core/data/src/main/java/com/etatech/hashiya/core/data/di/DataModule.kt`
- Create: `core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeAppUpdateRepository.kt`
- Test: `core/data/src/test/java/com/etatech/hashiya/core/data/repository/ConfigAppUpdateRepositoryTest.kt`

**Interfaces:**
- Consumes `AppConfigDataSource` and `NetworkPlatformConfig` (Task 2).
- Produces `data class RequiredUpdate(val storeUrl: String)` in `com.etatech.hashiya.core.model`.
- Produces `interface AppUpdateRepository { suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate? }` in `com.etatech.hashiya.core.data.repository`.
- Produces `class FakeAppUpdateRepository`, with `var result: RequiredUpdate?`, `val checkedVersionCodes: List<Long>`, `fun holdChecks()` and `fun releaseChecks()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.network.AppConfigDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.NetworkPlatformConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigAppUpdateRepositoryTest {
    private class StubDataSource(private val answer: () -> NetworkPlatformConfig?) : AppConfigDataSource {
        override suspend fun androidConfig(): NetworkPlatformConfig? = answer()
    }

    private fun repository(answer: () -> NetworkPlatformConfig?) = ConfigAppUpdateRepository(StubDataSource(answer))

    @Test
    fun aBuildBelowTheMinimumMustUpdate() = runTest {
        assertEquals(RequiredUpdate(STORE), repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(4))
    }

    @Test
    fun theMinimumBuildItselfIsAllowed() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(5))
    }

    @Test
    fun aNewerBuildIsAllowed() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(6))
    }

    @Test
    fun noAndroidEntryMeansNoBlock() = runTest {
        assertNull(repository { null }.requiredUpdate(1))
    }

    @Test
    fun noMinimumMeansNoBlock() = runTest {
        assertNull(repository { NetworkPlatformConfig(null, STORE) }.requiredUpdate(1))
    }

    @Test
    fun noStoreLinkMeansNoBlock() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, null) }.requiredUpdate(1))
    }

    @Test
    fun aStoreLinkThatIsNotHttpsMeansNoBlock() = runTest {
        for (link in listOf("http://play.google.com/store/apps/details?id=com.etatech.hashiya", "", "play store")) {
            assertNull(link, repository { NetworkPlatformConfig(5, link) }.requiredUpdate(1))
        }
    }

    @Test
    fun aNetworkFailureMeansNoBlock() = runTest {
        assertNull(repository { throw NetworkException(NetworkFailure.Connectivity) }.requiredUpdate(1))
    }

    @Test
    fun anUnexpectedErrorMeansNoBlock() = runTest {
        assertNull(repository { throw IllegalStateException("boom") }.requiredUpdate(1))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() = runTest {
        repository { throw CancellationException("left the screen") }.requiredUpdate(1)
    }

    private companion object {
        const val STORE = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*ConfigAppUpdateRepositoryTest" -q`
Expected: compilation fails with `Unresolved reference 'ConfigAppUpdateRepository'` (and `RequiredUpdate`).

- [ ] **Step 3: Implement**

`core/model/src/main/kotlin/com/etatech/hashiya/core/model/RequiredUpdate.kt`:
```kotlin
package com.etatech.hashiya.core.model

/** This build is no longer supported; [storeUrl] is the app's store page. */
data class RequiredUpdate(val storeUrl: String)
```

`core/data/src/main/java/com/etatech/hashiya/core/data/repository/AppUpdateRepository.kt`:
```kotlin
package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.network.AppConfigDataSource
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface AppUpdateRepository {
    /** A required update when [currentVersionCode] is below the published minimum; null otherwise, and on any failure. */
    suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate?
}

/** Never blocks by mistake: offline, an error, or a missing or malformed field all mean "no update required". */
internal class ConfigAppUpdateRepository @Inject constructor(private val dataSource: AppConfigDataSource) : AppUpdateRepository {
    override suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate? {
        val config = try {
            dataSource.androidConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val minimum = config.minimumVersionCode ?: return null
        val storeUrl = config.storeUrl?.takeIf { it.startsWith("https://") } ?: return null
        return if (currentVersionCode < minimum) RequiredUpdate(storeUrl) else null
    }
}
```
The test constructs `ConfigAppUpdateRepository` directly, and it's in the same module, so `internal` is fine.

In `DataModule.kt`, add the imports `com.etatech.hashiya.core.data.repository.AppUpdateRepository` and `com.etatech.hashiya.core.data.repository.ConfigAppUpdateRepository`. Then add:
```kotlin
    @Binds
    abstract fun bindAppUpdateRepository(impl: ConfigAppUpdateRepository): AppUpdateRepository
```

`core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeAppUpdateRepository.kt`:
```kotlin
package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.AppUpdateRepository
import com.etatech.hashiya.core.model.RequiredUpdate
import kotlinx.coroutines.CompletableDeferred

class FakeAppUpdateRepository : AppUpdateRepository {
    var result: RequiredUpdate? = null
    val checkedVersionCodes = mutableListOf<Long>()
    private var gate: CompletableDeferred<Unit>? = null

    /** Makes every following check wait until [releaseChecks], so tests can see a check in progress. */
    fun holdChecks() {
        gate = CompletableDeferred()
    }

    fun releaseChecks() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate? {
        checkedVersionCodes += currentVersionCode
        gate?.await()
        return result
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:testing:compileDebugKotlin spotlessCheck -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/model core/data core/testing
git commit -m "feat: decide on Android whether this build must update"
```

---

### Task 4: Android Update required screen (`core:designsystem`)

**Files:**
- Create: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/component/UpdateRequiredScreen.kt`
- Modify: `core/designsystem/src/main/java/com/etatech/hashiya/core/designsystem/icon/HashiyaIcons.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`, `core/designsystem/src/main/res/values-ar/strings.xml`
- Test: `core/designsystem/src/test/java/com/etatech/hashiya/core/designsystem/component/UpdateRequiredScreenTest.kt`, `.../component/UpdateRequiredScreenshotTest.kt`

**Interfaces:**
- Produces `@Composable fun UpdateRequiredScreen(onUpdate: () -> Unit, modifier: Modifier = Modifier)` in `com.etatech.hashiya.core.designsystem.component`.

- [ ] **Step 1: Write the failing tests**

`UpdateRequiredScreenTest.kt`:
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateRequiredScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheMessageAndTheUpdateButtonCallsBack() {
        var updates = 0
        composeRule.setContent { HashiyaTheme { UpdateRequiredScreen(onUpdate = { updates++ }) } }

        composeRule.onNodeWithText("Update required").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Update").performClick()
        assertEquals(1, updates)
    }
}
```

`UpdateRequiredScreenshotTest.kt`:
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class UpdateRequiredScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun updateRequired() = composeRule.captureScreenshot("update_required", variant, arabicText = "يلزم التحديث", wholeScreen = true) {
        UpdateRequiredScreen(onUpdate = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*UpdateRequired*" -q`
Expected: compilation fails with `Unresolved reference 'UpdateRequiredScreen'`.

- [ ] **Step 3: Implement**

In `HashiyaIcons.kt`, add the import `androidx.compose.material.icons.outlined.SystemUpdate` and the member:
```kotlin
    val Update: ImageVector = Icons.Outlined.SystemUpdate
```

In `values/strings.xml`, before `</resources>`:
```xml
    <string name="designsystem_update_required_title">Update required</string>
    <string name="designsystem_update_required_message">This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device.</string>
    <string name="designsystem_update_required_action">Update</string>
```
In `values-ar/strings.xml`, before `</resources>`:
```xml
    <string name="designsystem_update_required_title">يلزم التحديث</string>
    <string name="designsystem_update_required_message">لم يعد هذا الإصدار من حاشية مدعومًا. حدّث التطبيق لمتابعة استخدامه، وستبقى أوراقك المحفوظة على جهازك.</string>
    <string name="designsystem_update_required_action">تحديث</string>
```

`UpdateRequiredScreen.kt`:
```kotlin
package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons

/** Shown instead of the whole app when this build is no longer supported. [onUpdate] opens the store page. */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = HashiyaIcons.Update,
                title = stringResource(R.string.designsystem_update_required_title),
                message = stringResource(R.string.designsystem_update_required_message),
                actionLabel = stringResource(R.string.designsystem_update_required_action),
                onAction = onUpdate
            )
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest spotlessCheck -q`
Expected: BUILD SUCCESSFUL. With no recorded baseline and no record/verify flag, Roborazzi only captures, so the screenshot test passes. Don't record or commit a baseline locally: after the branch is pushed and CI runs again, record it with `bash scripts/record-screenshots-on-linux.sh`.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem
git commit -m "feat: add the Update required screen on Android"
```

---

### Task 5: Android app wiring (`app`)

**Files:**
- Create: `app/src/main/java/com/etatech/hashiya/update/AppUpdateViewModel.kt`
- Create: `app/src/main/java/com/etatech/hashiya/update/AppUpdateModule.kt`
- Modify: `app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt`
- Modify: `app/src/main/java/com/etatech/hashiya/MainActivity.kt`
- Modify: `app/build.gradle.kts` (test dependency on `:core:testing`)
- Test: `app/src/test/java/com/etatech/hashiya/update/AppUpdateViewModelTest.kt`, `app/src/test/java/com/etatech/hashiya/update/UpdateGateTest.kt`

**Interfaces:**
- Consumes `AppUpdateRepository`, `RequiredUpdate`, `FakeAppUpdateRepository` (Task 3) and `UpdateRequiredScreen` (Task 4).
- Produces `fun interface InstalledVersionCode { operator fun invoke(): Long }`.
- Produces `class AppUpdateViewModel`, with `val requiredUpdate: StateFlow<RequiredUpdate?>` and `fun check()`.
- Produces two new `HashiyaApp` parameters: `requiredUpdate: RequiredUpdate? = null` and `onOpenStore: (String) -> Unit = {}`.

- [ ] **Step 1: Add the test dependency**

In `app/build.gradle.kts`, under `dependencies`, add:
```kotlin
    testImplementation(project(":core:testing"))
```

- [ ] **Step 2: Write the failing tests**

`AppUpdateViewModelTest.kt`:
```kotlin
package com.etatech.hashiya.update

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.testing.FakeAppUpdateRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAppUpdateRepository()
    private val update = RequiredUpdate("https://play.google.com/store/apps/details?id=com.etatech.hashiya")

    private fun viewModel() = AppUpdateViewModel(repository) { 7L }

    @Test
    fun checksTheInstalledVersionCode() = runTest {
        viewModel().check()

        assertEquals(listOf(7L), repository.checkedVersionCodes)
    }

    @Test
    fun blocksWhenAnUpdateIsRequired() = runTest {
        repository.result = update
        val viewModel = viewModel()

        viewModel.check()

        assertEquals(update, viewModel.requiredUpdate.value)
    }

    @Test
    fun staysUnblockedWhileTheCheckIsRunning() = runTest {
        repository.result = update
        repository.holdChecks()
        val viewModel = viewModel()

        viewModel.check()

        assertNull(viewModel.requiredUpdate.value)
        repository.releaseChecks()
        assertEquals(update, viewModel.requiredUpdate.value)
    }

    @Test
    fun aReturnDuringACheckDoesNotStartAnother() = runTest {
        repository.holdChecks()
        val viewModel = viewModel()

        viewModel.check()
        viewModel.check()
        repository.releaseChecks()

        assertEquals(1, repository.checkedVersionCodes.size)
    }

    @Test
    fun staysBlockedAfterALaterCheckFindsNothing() = runTest {
        repository.result = update
        val viewModel = viewModel()
        viewModel.check()

        repository.result = null
        viewModel.check()

        assertEquals(update, viewModel.requiredUpdate.value)
        assertEquals(1, repository.checkedVersionCodes.size)
    }

    @Test
    fun checksAgainOnReturnWhenNotBlocked() = runTest {
        val viewModel = viewModel()
        viewModel.check()
        viewModel.check()

        assertEquals(2, repository.checkedVersionCodes.size)
    }
}
```

`UpdateGateTest.kt`:
```kotlin
package com.etatech.hashiya.update

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.navigation.HashiyaApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plain Application: with an update required, HashiyaApp never builds the Hilt-backed screens. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRequiredUpdateShowsOnlyTheUpdateScreen() {
        var opened: String? = null
        composeRule.setContent {
            HashiyaTheme { HashiyaApp(requiredUpdate = RequiredUpdate(STORE), onOpenStore = { opened = it }) }
        }

        composeRule.onNodeWithText("Update required").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertDoesNotExist()

        composeRule.onNodeWithText("Update").performClick()
        assertEquals(STORE, opened)
    }

    private companion object {
        const val STORE = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
    }
}
```

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*update*" -q`
Expected: compilation fails with `Unresolved reference 'AppUpdateViewModel'` and `No parameter with name 'requiredUpdate'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/etatech/hashiya/update/AppUpdateViewModel.kt`:
```kotlin
package com.etatech.hashiya.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.AppUpdateRepository
import com.etatech.hashiya.core.model.RequiredUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** This install's versionCode. */
fun interface InstalledVersionCode {
    operator fun invoke(): Long
}

/**
 * Whether this build must update. [check] runs on every start; the app shows normally while it runs.
 * Once blocked, it stays blocked for the life of the process, so a later failed check never lets the user back in.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val installedVersionCode: InstalledVersionCode
) : ViewModel() {
    private val _requiredUpdate = MutableStateFlow<RequiredUpdate?>(null)
    val requiredUpdate: StateFlow<RequiredUpdate?> = _requiredUpdate.asStateFlow()

    private var running: Job? = null

    fun check() {
        if (_requiredUpdate.value != null || running?.isActive == true) return
        running = viewModelScope.launch {
            repository.requiredUpdate(installedVersionCode())?.let { _requiredUpdate.value = it }
        }
    }
}
```

`app/src/main/java/com/etatech/hashiya/update/AppUpdateModule.kt`:
```kotlin
package com.etatech.hashiya.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides
    fun provideInstalledVersionCode(@ApplicationContext context: Context): InstalledVersionCode = InstalledVersionCode {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }
}
```

In `HashiyaApp.kt`, add the imports `com.etatech.hashiya.core.designsystem.component.UpdateRequiredScreen` and `com.etatech.hashiya.core.model.RequiredUpdate`. Then change the signature and the start of the body:
```kotlin
@Composable
fun HashiyaApp(
    navController: NavHostController = rememberNavController(),
    pendingSearch: SearchRoute? = null,
    onPendingSearchHandled: () -> Unit = {},
    requiredUpdate: RequiredUpdate? = null,
    onOpenStore: (String) -> Unit = {}
) {
    if (requiredUpdate != null) {
        UpdateRequiredScreen(onUpdate = { onOpenStore(requiredUpdate.storeUrl) })
        return
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
```
The rest of the body stays as it is.

In `MainActivity.kt`, add the imports `androidx.activity.viewModels`, `androidx.compose.runtime.collectAsState`, `androidx.core.net.toUri` and `com.etatech.hashiya.update.AppUpdateViewModel`. Then:
```kotlin
    private val appUpdate: AppUpdateViewModel by viewModels()
```
In `onCreate`, replace the `setContent` block with:
```kotlin
        setContent {
            val requiredUpdate by appUpdate.requiredUpdate.collectAsState()
            HashiyaTheme {
                HashiyaApp(
                    pendingSearch = pendingSearch,
                    onPendingSearchHandled = { pendingSearch = null },
                    requiredUpdate = requiredUpdate,
                    onOpenStore = ::openStore
                )
            }
        }
```
And add to the class:
```kotlin
    /** Launch and every return to the foreground. */
    override fun onStart() {
        super.onStart()
        appUpdate.check()
    }

    /** A device without a store app or browser keeps showing the screen instead of crashing. */
    private fun openStore(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest spotlessCheck -q && ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. The new tests pass, the existing `HashiyaAppNavigationTest` and `ShareNavigationTest` still pass (with no update required they see the normal app), and the app builds. Those two tests start the real `MainActivity`, so each now makes one real request for the live config. Offline or not, that can only result in no block, so they stay green; they just aren't network-free any more.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat: block unsupported Android builds with the Update required screen"
```

---

### Task 6: iOS config client (`HashiyaNetwork`)

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaNetworkTests/AppConfigClientTests.swift`

**Interfaces:**
- Produces `public protocol AppConfigService: Sendable { func iosConfig() async throws -> PlatformAppConfig? }`.
- Produces `public struct PlatformAppConfig: Decodable, Equatable, Sendable { minimumBuild: Int?; storeUrl: String? }`.
- Produces `public final class AppConfigClient: AppConfigService`, with `init(session:url:)` and `static let url`.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct AppConfigClientTests {
    private let appStore = "https://apps.apple.com/app/id0000000000"
    private let sample = """
    {
      "android": { "minimumVersionCode": 1, "storeUrl": "https://play.google.com/store/apps/details?id=com.etatech.hashiya" },
      "ios": { "minimumBuild": 3, "storeUrl": "https://apps.apple.com/app/id0000000000" }
    }
    """

    private func client(_ server: URLProtocolStub.Server) -> AppConfigClient {
        AppConfigClient(session: server.session)
    }

    @Test func requestsTheFileWithNoQueryOrKey() async throws {
        let server = URLProtocolStub.Server(always: .json(sample))

        _ = try await client(server).iosConfig()

        let request = try #require(server.requests.last)
        #expect(request.url?.host() == "fadyfouad.github.io")
        #expect(request.url?.path() == "/Hashiya-Privacy-Policy/app-config.json")
        #expect(request.url?.query() == nil)
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
    }

    @Test func readsTheIOSEntry() async throws {
        let server = URLProtocolStub.Server(always: .json(sample))
        #expect(try await client(server).iosConfig() == PlatformAppConfig(minimumBuild: 3, storeUrl: appStore))
    }

    @Test func ignoresUnknownKeys() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"ios":{"minimumBuild":2,"storeUrl":"\#(appStore)","message":"x"},"web":{}}"#))
        #expect(try await client(server).iosConfig() == PlatformAppConfig(minimumBuild: 2, storeUrl: appStore))
    }

    @Test func aMissingIOSEntryIsNil() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"android":{"minimumVersionCode":5}}"#))
        #expect(try await client(server).iosConfig() == nil)
    }

    @Test(arguments: [#"{"ios":{"minimumBuild":"2"}}"#, #"{"ios":{"minimumBuild":2.5}}"#, "<html>Not found</html>"])
    func unreadableContentIsMalformed(body: String) async {
        let server = URLProtocolStub.Server(always: .json(body))
        await #expect(throws: NetworkFailure.malformedResponse) { try await client(server).iosConfig() }
    }

    @Test func aMissingFileIsAnHTTPFailure() async {
        let server = URLProtocolStub.Server(always: .status(404, body: Data("Not found".utf8)))
        await #expect(throws: NetworkFailure.http(code: 404, usedUserKey: false)) { try await client(server).iosConfig() }
    }

    @Test func noConnectionIsConnectivity() async {
        let server = URLProtocolStub.Server(always: .failure(.notConnectedToInternet))
        await #expect(throws: NetworkFailure.connectivity) { try await client(server).iosConfig() }
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ../../build/ios-tests -only-testing:HashiyaNetworkTests/AppConfigClientTests 2>&1 | grep -E "error:|✘|TEST (SUCCEEDED|FAILED)" | head`
Expected: `error: cannot find 'AppConfigClient' in scope`, then TEST FAILED.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift`:
```swift
import Foundation

/// The iOS entry of the app's remote config.
public struct PlatformAppConfig: Decodable, Equatable, Sendable {
    public let minimumBuild: Int?
    public let storeUrl: String?

    public init(minimumBuild: Int?, storeUrl: String?) {
        self.minimumBuild = minimumBuild
        self.storeUrl = storeUrl
    }
}

public protocol AppConfigService: Sendable {
    /// The iOS entry, or nil when the file has none. Throws `NetworkFailure` when the file can't be fetched or read.
    func iosConfig() async throws -> PlatformAppConfig?
}

/// Reads the app's remote config from GitHub Pages. It has its own URLSession with short timeouts, sends no
/// OpenAlex key and nothing about the user, and logs nothing. Cancelling the calling task cancels the request.
public final class AppConfigClient: AppConfigService {
    public static let url = URL(string: "https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json")!

    private struct File: Decodable {
        let ios: PlatformAppConfig?
    }

    private let session: URLSession
    private let url: URL

    public init(session: URLSession = URLSession(configuration: AppConfigClient.makeConfiguration()), url: URL = AppConfigClient.url) {
        self.session = session
        self.url = url
    }

    /// Ephemeral, with 5-second timeouts, so a slow GitHub never holds the check for long.
    public static func makeConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 5
        return configuration
    }

    public func iosConfig() async throws -> PlatformAppConfig? {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch is CancellationError {
            throw CancellationError()
        } catch is URLError {
            throw NetworkFailure.connectivity
        } catch {
            throw NetworkFailure.unknown
        }
        guard let http = response as? HTTPURLResponse else { throw NetworkFailure.unknown }
        guard (200...299).contains(http.statusCode) else {
            throw NetworkFailure.http(code: http.statusCode, usedUserKey: false)
        }
        do {
            return try JSONDecoder().decode(File.self, from: data).ios
        } catch {
            throw NetworkFailure.malformedResponse
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run the Step 2 command.
Expected: `✔ Suite AppConfigClientTests passed`, then TEST SUCCEEDED.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaNetwork/AppConfigClient.swift ios/HashiyaKit/Tests/HashiyaNetworkTests/AppConfigClientTests.swift
git commit -m "feat: fetch the app config from GitHub Pages on iOS"
```

---

### Task 7: iOS repository and update model (`HashiyaModel`, `HashiyaData`, `HashiyaTesting`)

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaModel/RequiredUpdate.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaTesting/FakeAppUpdateRepository.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/AppUpdateTests.swift`

**Interfaces:**
- Consumes `AppConfigService` and `PlatformAppConfig` (Task 6).
- Produces `public struct RequiredUpdate: Equatable, Sendable { public let storeURL: URL }` in HashiyaModel.
- Produces `public protocol AppUpdateRepository: Sendable { func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? }`.
- Produces `public struct ConfigAppUpdateRepository`, with `init(service:)` and `static func live()`.
- Produces `@MainActor @Observable public final class AppUpdateModel`, with `init(repository:currentBuild: String?)`, `requiredUpdate` and `check() async`.
- Produces `public final class FakeAppUpdateRepository`, with `init(result:)`, `setResult(_:)` and `checkedBuilds`.

- [ ] **Step 1: Write the failing tests**

`ios/HashiyaKit/Tests/HashiyaDataTests/AppUpdateTests.swift`:
```swift
import Foundation
import HashiyaData
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import Testing

private struct StubConfigService: AppConfigService {
    let answer: @Sendable () throws -> PlatformAppConfig?
    func iosConfig() async throws -> PlatformAppConfig? { try answer() }
}

private let store = "https://apps.apple.com/app/id0000000000"

private func repository(_ answer: @escaping @Sendable () throws -> PlatformAppConfig?) -> ConfigAppUpdateRepository {
    ConfigAppUpdateRepository(service: StubConfigService(answer: answer))
}

struct ConfigAppUpdateRepositoryTests {
    @Test func aBuildBelowTheMinimumMustUpdate() async {
        let update = await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: store) }.requiredUpdate(currentBuild: 4)
        #expect(update == RequiredUpdate(storeURL: URL(string: store)!))
    }

    @Test(arguments: [5, 6])
    func theMinimumAndNewerBuildsAreAllowed(build: Int) async {
        #expect(await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: store) }.requiredUpdate(currentBuild: build) == nil)
    }

    @Test func noIOSEntryMeansNoBlock() async {
        #expect(await repository { nil }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test func noMinimumMeansNoBlock() async {
        #expect(await repository { PlatformAppConfig(minimumBuild: nil, storeUrl: store) }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test(arguments: [nil, "", "http://apps.apple.com/app/id0000000000", "app store"] as [String?])
    func aStoreLinkThatIsNotHTTPSMeansNoBlock(link: String?) async {
        #expect(await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: link) }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test func aFailureMeansNoBlock() async {
        #expect(await repository { throw NetworkFailure.connectivity }.requiredUpdate(currentBuild: 1) == nil)
    }
}

@MainActor
struct AppUpdateModelTests {
    private let update = RequiredUpdate(storeURL: URL(string: store)!)

    @Test func checksTheBuildAsANumber() async {
        let repository = FakeAppUpdateRepository()
        await AppUpdateModel(repository: repository, currentBuild: "7").check()
        #expect(repository.checkedBuilds == [7])
    }

    @Test func blocksWhenAnUpdateIsRequired() async {
        let model = AppUpdateModel(repository: FakeAppUpdateRepository(result: update), currentBuild: "1")
        await model.check()
        #expect(model.requiredUpdate == update)
    }

    @Test(arguments: [nil, "", "1.0.1", "abc"] as [String?])
    func aBuildThatIsNotAWholeNumberIsNeverChecked(build: String?) async {
        let repository = FakeAppUpdateRepository(result: update)
        let model = AppUpdateModel(repository: repository, currentBuild: build)
        await model.check()
        #expect(model.requiredUpdate == nil)
        #expect(repository.checkedBuilds.isEmpty)
    }

    @Test func staysBlockedAfterALaterCheckFindsNothing() async {
        let repository = FakeAppUpdateRepository(result: update)
        let model = AppUpdateModel(repository: repository, currentBuild: "1")
        await model.check()

        repository.setResult(nil)
        await model.check()

        #expect(model.requiredUpdate == update)
        #expect(repository.checkedBuilds == [1])
    }

    @Test func checksAgainWhenNotBlocked() async {
        let repository = FakeAppUpdateRepository()
        let model = AppUpdateModel(repository: repository, currentBuild: "1")
        await model.check()
        await model.check()
        #expect(repository.checkedBuilds == [1, 1])
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ../../build/ios-tests -only-testing:HashiyaDataTests/ConfigAppUpdateRepositoryTests -only-testing:HashiyaDataTests/AppUpdateModelTests 2>&1 | grep -E "error:|✘|TEST (SUCCEEDED|FAILED)" | head`
Expected: `error: cannot find 'ConfigAppUpdateRepository' in scope`, then TEST FAILED.

- [ ] **Step 3: Implement**

`ios/HashiyaKit/Sources/HashiyaModel/RequiredUpdate.swift`:
```swift
import Foundation

/// This build is no longer supported; `storeURL` is the app's store page.
public struct RequiredUpdate: Equatable, Sendable {
    public let storeURL: URL

    public init(storeURL: URL) {
        self.storeURL = storeURL
    }
}
```

`ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift`:
```swift
import Foundation
import HashiyaModel
import HashiyaNetwork
import Observation

public protocol AppUpdateRepository: Sendable {
    /// A required update when `currentBuild` is below the published minimum; nil otherwise, and on any failure.
    func requiredUpdate(currentBuild: Int) async -> RequiredUpdate?
}

/// Never blocks by mistake: offline, an error, or a missing or malformed field all mean "no update required".
public struct ConfigAppUpdateRepository: AppUpdateRepository {
    private let service: any AppConfigService

    public init(service: any AppConfigService) {
        self.service = service
    }

    /// The real one, reading GitHub Pages.
    public static func live() -> ConfigAppUpdateRepository {
        ConfigAppUpdateRepository(service: AppConfigClient())
    }

    public func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? {
        guard let config = try? await service.iosConfig(),
              let minimum = config.minimumBuild,
              let link = config.storeUrl, let storeURL = URL(string: link), storeURL.scheme == "https",
              currentBuild < minimum else { return nil }
        return RequiredUpdate(storeURL: storeURL)
    }
}

/// Whether this build must update. `check()` runs on launch and every return to the foreground; the app shows
/// normally while it runs. Once blocked, it stays blocked for the life of the process.
@MainActor
@Observable
public final class AppUpdateModel {
    public private(set) var requiredUpdate: RequiredUpdate?

    @ObservationIgnored private let repository: any AppUpdateRepository
    @ObservationIgnored private let currentBuild: Int?
    @ObservationIgnored private var isChecking = false

    /// - Parameter currentBuild: `CFBundleVersion`; a value that isn't a whole number means no check.
    public init(repository: any AppUpdateRepository, currentBuild: String?) {
        self.repository = repository
        self.currentBuild = currentBuild.flatMap { Int($0) }
    }

    public func check() async {
        guard requiredUpdate == nil, !isChecking, let currentBuild else { return }
        isChecking = true
        defer { isChecking = false }
        if let update = await repository.requiredUpdate(currentBuild: currentBuild) {
            requiredUpdate = update
        }
    }
}
```

`ios/HashiyaKit/Sources/HashiyaTesting/FakeAppUpdateRepository.swift`:
```swift
import HashiyaData
import HashiyaModel
import os

public final class FakeAppUpdateRepository: AppUpdateRepository, Sendable {
    private struct State {
        var result: RequiredUpdate?
        var checkedBuilds: [Int] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    public init(result: RequiredUpdate? = nil) {
        state = OSAllocatedUnfairLock(initialState: State(result: result))
    }

    public func setResult(_ result: RequiredUpdate?) {
        state.withLock { $0.result = result }
    }

    /// Every build number checked so far, oldest first.
    public var checkedBuilds: [Int] { state.withLock { $0.checkedBuilds } }

    public func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? {
        state.withLock {
            $0.checkedBuilds.append(currentBuild)
            return $0.result
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run the Step 2 command.
Expected: both suites pass, then TEST SUCCEEDED.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaModel/RequiredUpdate.swift ios/HashiyaKit/Sources/HashiyaData/AppUpdateRepository.swift ios/HashiyaKit/Sources/HashiyaTesting/FakeAppUpdateRepository.swift ios/HashiyaKit/Tests/HashiyaDataTests/AppUpdateTests.swift
git commit -m "feat: decide on iOS whether this build must update"
```

---

### Task 8: iOS Update required view (`HashiyaDesignSystem`)

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/UpdateRequiredView.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings`
- Test: `ios/HashiyaKit/Tests/HashiyaDesignSystemTests/DesignSystemSnapshotTests.swift` (one new test)

**Interfaces:**
- Produces `public struct UpdateRequiredView: View`, with `init(onUpdate: @escaping () -> Void)`.

- [ ] **Step 1: Write the failing test**

Add to `DesignSystemSnapshotTests`:
```swift
    @Test func updateRequired() {
        assertHashiyaSnapshots(of: UpdateRequiredView(onUpdate: {}), named: "updateRequired", arabicText: "يلزم التحديث")
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ../../build/ios-tests -only-testing:HashiyaDesignSystemTests/DesignSystemSnapshotTests/updateRequired\(\) 2>&1 | grep -E "error:|TEST (SUCCEEDED|FAILED)" | head`
Expected: `error: cannot find 'UpdateRequiredView' in scope`, then TEST FAILED.

- [ ] **Step 3: Implement**

Add the three strings to `Localizable.xcstrings`, in the same shape as `designsystem.save`. Keys are sorted alphabetically in that file, so run this from the repo root:
```bash
python3 - <<'EOF'
import json
p = "ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings"
data = json.load(open(p, encoding="utf-8"))
def entry(en, ar):
    return {"extractionState": "manual", "localizations": {
        "ar": {"stringUnit": {"state": "translated", "value": ar}},
        "en": {"stringUnit": {"state": "translated", "value": en}}}}
data["strings"]["designsystem.updateRequired.action"] = entry("Update", "تحديث")
data["strings"]["designsystem.updateRequired.message"] = entry(
    "This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device.",
    "لم يعد هذا الإصدار من حاشية مدعومًا. حدّث التطبيق لمتابعة استخدامه، وستبقى أوراقك المحفوظة على جهازك.")
data["strings"]["designsystem.updateRequired.title"] = entry("Update required", "يلزم التحديث")
data["strings"] = dict(sorted(data["strings"].items()))
json.dump(data, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2, separators=(",", " : "))
EOF
git diff --stat ios/HashiyaKit/Sources/HashiyaDesignSystem/Resources/Localizable.xcstrings
```
Expected: only additions, about 42 lines. If the diff rewrites unrelated lines, the file's formatting differs from `json.dump`'s. In that case, revert with `git checkout` and add the three entries by hand, next to `designsystem.untitled`.

`ios/HashiyaKit/Sources/HashiyaDesignSystem/Components/UpdateRequiredView.swift`:
```swift
import SwiftUI

/// Shown instead of the whole app when this build is no longer supported. `onUpdate` opens the store page.
public struct UpdateRequiredView: View {
    private let onUpdate: () -> Void

    public init(onUpdate: @escaping () -> Void) {
        self.onUpdate = onUpdate
    }

    public var body: some View {
        EmptyStateView(
            icon: "arrow.down.app",
            title: L10n.string("designsystem.updateRequired.title"),
            message: L10n.string("designsystem.updateRequired.message"),
            actionTitle: L10n.string("designsystem.updateRequired.action"),
            action: onUpdate
        )
        .background(HashiyaColors.surface.ignoresSafeArea())
    }
}
```

- [ ] **Step 4: Run the test**

Run the Step 2 command. It now compiles. Without a reference image, swift-snapshot-testing records one and reports the test as failed ("No reference was found on disk"). That's expected: baselines come from CI.

Check the recorded images look right (English, Arabic right-to-left, light and dark):
```bash
open ios/HashiyaKit/Tests/HashiyaDesignSystemTests/__Snapshots__/DesignSystemSnapshotTests/updateRequired*
```
Then delete them, so no locally recorded baseline is committed:
```bash
rm ios/HashiyaKit/Tests/HashiyaDesignSystemTests/__Snapshots__/DesignSystemSnapshotTests/updateRequired*
```
Also run `python3 ios/scripts/check-translations.py`. Expected: no missing translations.

- [ ] **Step 5: Commit**

```bash
git add ios/HashiyaKit/Sources/HashiyaDesignSystem ios/HashiyaKit/Tests/HashiyaDesignSystemTests/DesignSystemSnapshotTests.swift
git commit -m "feat: add the Update required view on iOS"
```

---

### Task 9: iOS app wiring

**Files:**
- Modify: `ios/Hashiya/AppContainer.swift`
- Modify: `ios/Hashiya/UITestingStubs.swift`
- Modify: `ios/Hashiya/RootView.swift`

**Interfaces:**
- Consumes `ConfigAppUpdateRepository.live()`, `AppUpdateModel` (Task 7) and `UpdateRequiredView` (Task 8).

- [ ] **Step 1: Wire the repository into the container**

In `AppContainer.swift`, add `import HashiyaModel` if needed, plus a stored property and an extra init parameter:
```swift
    let appUpdateRepository: any AppUpdateRepository

    init(dependencies: LiveDependencies, appUpdateRepository: any AppUpdateRepository) {
        libraryRepository = dependencies.libraryRepository
        searchRepository = dependencies.searchRepository
        lookupRepository = dependencies.lookupRepository
        preferences = dependencies.preferences
        self.appUpdateRepository = appUpdateRepository
    }
```
In `make(arguments:)`, pass it:
```swift
        if arguments.contains("-ui-testing") {
            return AppContainer(dependencies: UITestingStubs.dependencies(), appUpdateRepository: UITestingStubs.appUpdateRepository)
        }
        #endif
        do {
            return AppContainer(dependencies: try LiveDependencies.live(), appUpdateRepository: ConfigAppUpdateRepository.live())
```
And add a factory:
```swift
    func makeAppUpdateModel() -> AppUpdateModel {
        AppUpdateModel(repository: appUpdateRepository, currentBuild: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String)
    }
```

In `UITestingStubs.swift`, inside `enum UITestingStubs`, add:
```swift
    /// UI tests never see the Update required screen.
    static let appUpdateRepository: any AppUpdateRepository = NoUpdateRequired()
```
And below the enum, next to the other private stubs:
```swift
private struct NoUpdateRequired: AppUpdateRepository {
    func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? { nil }
}
```

- [ ] **Step 2: Gate the root view**

In `RootView.swift`:
- Add `@State private var appUpdate: AppUpdateModel` and `@Environment(\.openURL) private var openURL`.
- In `init`, add `_appUpdate = State(initialValue: container.makeAppUpdateModel())`.
- Rename the current `var body: some View` to `private var tabs: some View`. Keep `TabView`, `.tint`, `.sheet` and `.task` on it. Move the `.onChange(of: scenePhase, initial: true)` modifier off it.
- Add the new body:
```swift
    var body: some View {
        Group {
            if appUpdate.requiredUpdate != nil {
                UpdateRequiredView(onUpdate: openStore)
            } else {
                tabs
            }
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            switch phase {
            case .active:
                // Papers saved in the Share Extension appear in the Library and as "In library".
                SharedLibraryDatabase.resume()
                Task { await container.libraryRepository.refreshAfterExternalChanges() }
                Task { await appUpdate.check() }
            case .background:
                SharedLibraryDatabase.suspend()
            default:
                break
            }
        }
    }

    private func openStore() {
        if let url = appUpdate.requiredUpdate?.storeURL { openURL(url) }
    }
```

- [ ] **Step 3: Build and run the UI tests**

Run:
```bash
cd ios && xcodegen generate -q && xcodebuild test -project Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ../build/ios-tests -only-testing:HashiyaUITests 2>&1 | grep -E "error:|Executed|TEST (SUCCEEDED|FAILED)" | tail -5
```
Expected: the app builds and the existing UI tests pass (they use `-ui-testing`, so the update check never blocks), then TEST SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add ios/Hashiya/AppContainer.swift ios/Hashiya/UITestingStubs.swift ios/Hashiya/RootView.swift
git commit -m "feat: block unsupported iOS builds with the Update required screen"
```

---

### Task 10: Publish the config file, update the privacy policy and document the switch

**Files:**
- Create in `FadyFouad/Hashiya-Privacy-Policy`: `app-config.json`
- Modify in `FadyFouad/Hashiya-Privacy-Policy`: `index.html`
- Modify: `docs/release.md`

**Interfaces:** Produces the live file that both clients read (Global Constraints: config URL).

- [ ] **Step 1: Add the config file and the privacy note**

```bash
git clone https://github.com/FadyFouad/Hashiya-Privacy-Policy.git build/privacy-site
cd build/privacy-site
git config user.name "Fady" && git config user.email "fady.fouad.a@gmail.com"
cat > app-config.json <<'EOF'
{
  "android": {
    "minimumVersionCode": 1,
    "storeUrl": "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
  },
  "ios": {
    "minimumBuild": 1,
    "storeUrl": "https://apps.apple.com/app/id0000000000"
  }
}
EOF
python3 -m json.tool app-config.json > /dev/null && echo valid
```
In `index.html`, add a paragraph after the English list of services, directly before the paragraph that starts "These requests don't include your name":
```html
      <p>Each time it opens, Hashiya also checks whether your version is still supported by downloading a small public file from GitHub Pages (<span class="host">fadyfouad.github.io</span>), operated by GitHub. That request contains nothing about you.</p>
```
And after the Arabic list, directly before the paragraph that starts "لا تتضمن هذه الطلبات":
```html
      <p>ويتحقق حاشية أيضًا عند كل فتح من أن إصدارك ما زال مدعومًا، بتنزيل ملف عام صغير من GitHub Pages ‏(<span class="host">fadyfouad.github.io</span>) التابع لشركة GitHub، ولا يتضمن هذا الطلب أي شيء عنك.</p>
```

- [ ] **Step 2: Publish and verify**

```bash
git add app-config.json index.html
git commit -m "Add the app config and mention the version check in the privacy policy"
git log --format='%an <%ae>' -1   # expect: Fady <fady.fouad.a@gmail.com>
git push origin main
cd ../..
for i in $(seq 1 18); do c=$(curl -s -o /dev/null -w "%{http_code}" https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json); [ "$c" = 200 ] && break; sleep 10; done
curl -s https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json | python3 -m json.tool
rm -rf build/privacy-site
```
Expected: the JSON above, served with HTTP 200.

- [ ] **Step 3: Document the switch in the runbook**

Add this section to `docs/release.md`, before "## Each release":
````markdown
## Forcing an update

Both apps read `app-config.json` from the [Hashiya-Privacy-Policy](https://github.com/FadyFouad/Hashiya-Privacy-Policy) repo on every launch and every return to the foreground. A build lower than its platform's minimum shows a full-screen "Update required" screen whose button opens the store page.

1. Find the first good build number: `versionCode` on Android, the build (`CURRENT_PROJECT_VERSION`) on iOS.
2. In `app-config.json`, set `android.minimumVersionCode` or `ios.minimumBuild` to it. Before the first iOS block, replace the placeholder `id0000000000` in `ios.storeUrl` with the real App Store ID.
3. Check the number twice: a minimum above every released build blocks everyone. Then push.
4. GitHub Pages caches the file for 10 minutes, so it takes effect within about 10 minutes and the user's next return to the app.

If the file can't be read (offline, a typo in the JSON, GitHub down), nobody is blocked. To undo a block, lower the number and push; blocked users get back in after restarting the app.
````

- [ ] **Step 4: Commit**

```bash
git add docs/release.md
git commit -m "docs: explain how to force an update"
```

---

## Final verification

- [ ] `./gradlew testDebugUnitTest spotlessCheck lintDebug -q`: BUILD SUCCESSFUL.
- [ ] iOS: `cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ../../build/ios-tests`. The new suites pass. The known flaky `eventually` tests and the snapshot tests without CI references may fail, exactly as on `main`.
- [ ] `git status --short` shows no locally recorded snapshot or screenshot images.
- [ ] `git log --format='%an <%ae>' origin/main..HEAD` shows only `Fady <fady.fouad.a@gmail.com>`.
- [ ] **On-device check, before release only.** Nothing has been released yet, so briefly raise both minimums to 99 in the live `app-config.json` and push. After up to 10 minutes, open each app (Android emulator or phone, iOS simulator):
  - Each shows the Update required screen.
  - Update opens the store link.
  - In Arabic, the screen is right-to-left and fully translated.
  - In airplane mode, each app opens normally.

  Then set both minimums back to 1, push, and confirm with `curl -s https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json`.
- [ ] Delete `build/ios-tests` to free disk space.
