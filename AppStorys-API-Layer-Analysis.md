# AppStorys Android SDK - API & Data Layer Deep Analysis

## 1️⃣ Big Picture — API Flow Overview

### What This SDK Does
**AppStorys** is an **SDK (Software Development Kit)** for Android apps that provides dynamic in-app engagement components—banners, modals, floaters, CSAT (Customer Satisfaction), reels, stories, tooltips, PIP videos, surveys, bottom sheets, scratch cards, spin-the-wheel, and milestones. All content is served from a remote backend and cached locally.

### APIs This App Depends On

| Service | Base URL | Purpose |
|---------|----------|---------|
| **Main API** | `https://backend.appstorys.co/` | Content delivery, feedback capture, element identification |
| **User/WebSocket API** | `https://users.appstorys.co/` | User validation, campaign eligibility, user properties |
| **CDN** (S3) | `https://dev-cdn-campaign-appstorys.s3.ap-south-1.amazonaws.com/` | Pre-built campaigns.json file |
| **Event Tracking** | `https://tracking.appstorys.co/capture-event` | Analytics and event capture |

### Features That Depend on Backend

| Feature | Campaign Type Code | Backend Dependency |
|---------|-------------------|-------------------|
| Banners | `BAN` | Campaign data + images |
| Floaters | `FLT` | Campaign data + images/Lottie |
| CSAT Dialogs | `CSAT` | Campaign data + feedback submission |
| Widgets | `WID` | Campaign data + carousel images |
| Reels | `REL` | Video URLs + like tracking |
| Tooltips | `TTP` | Element targeting + screenshots |
| PIP Video | `PIP` | Video URLs |
| Bottom Sheets | `BTS` | Dynamic elements |
| Surveys | `SUR` | Questions + response capture |
| Modals | `MOD` | Rich content popups |
| Stories | `STR` | Story groups + slides |
| Scratch Cards | `SCRT` | Gamification |
| Spin the Wheel | `STW` | Gamification |
| Milestones | `MIL` | Progress tracking |

### Complete Data Journey: App Launch → UI Update

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           INITIALIZATION FLOW                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  1. App Calls AppStorys.initialize()                                              │
│           │                                                                       │
│           ▼                                                                       │
│  2. AppStorys sets context, appId, accountId, userId                              │
│           │                                                                       │
│           ▼                                                                       │
│  3. ApiRepository.getAccessToken() called                                         │
│           │                                                                       │
│           ▼                                                                       │
│  4. webSocketApiService.validateAccount() → POST /{accountId}/validate-account    │
│           │                                                                       │
│           ▼                                                                       │
│  5. Retrofit serializes ValidateAccountRequest                                    │
│           │                                                                       │
│           ▼                                                                       │
│  6. OkHttp sends request with logging interceptor                                 │
│           │                                                                       │
│           ▼                                                                       │
│  7. Server returns ValidateAccountResponse { access_token }                       │
│           │                                                                       │
│           ▼                                                                       │
│  8. accessToken stored in AppStorys.accessToken                                   │
│           │                                                                       │
│           ▼                                                                       │
│  9. sdkState = AppStorysSdkState.Initialized                                      │
│           │                                                                       │
│           ▼                                                                       │
│  10. getScreenCampaigns("Home Screen") automatically called                       │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CAMPAIGN FETCH FLOW                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  1. AppStorys.getScreenCampaigns(screenName)                                      │
│           │                                                                       │
│           ▼                                                                       │
│  2. Repository.getScreenCampaignsData(accessToken, accountId, screenName, userId) │
│           │                                                                       │
│           ▼                                                                       │
│  3. webSocketApiService.getEligibleCampaigns()                                    │
│      → POST /v2/{accountId}/track-user-res                                        │
│           │                                                                       │
│           ▼                                                                       │
│  4. Server returns EligibleCampaignsResponse {                                    │
│        eligibleCampaignList, variants, personalization_data, test_user            │
│      }                                                                            │
│           │                                                                       │
│           ▼                                                                       │
│  5. fetchCampaignsJson(accountId) → HTTP GET to S3 CDN with ETag caching          │
│           │                                                                       │
│           ▼                                                                       │
│  6. If 200: Parse JSON, cache to SharedPreferences                                │
│      If 304: Use cached campaigns.json                                            │
│           │                                                                       │
│           ▼                                                                       │
│  7. Check for missing campaigns → loadMissingCampaigns() if needed                │
│           │                                                                       │
│           ▼                                                                       │
│  8. Filter campaigns by screenName + eligibility                                  │
│           │                                                                       │
│           ▼                                                                       │
│  9. Apply A/B test variants via extractVariantFromCampaign()                      │
│           │                                                                       │
│           ▼                                                                       │
│  10. Return ScreenCampaignResult                                                  │
│           │                                                                       │
│           ▼                                                                       │
│  11. campaigns.emit(filteredCampaigns) → MutableStateFlow                         │
│           │                                                                       │
│           ▼                                                                       │
│  12. Composables observe campaigns.collectAsStateWithLifecycle()                  │
│           │                                                                       │
│           ▼                                                                       │
│  13. UI recomposes with campaign data                                             │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2️⃣ Networking Stack Used

### Libraries Identified

| Library | Version | Purpose |
|---------|---------|---------|
| **Retrofit** | 2.9.0 | HTTP client wrapper |
| **OkHttp** | 4.12.0 | Underlying HTTP transport |
| **Kotlinx Serialization** | 1.6.3 | JSON parsing (NOT Gson for network) |
| **Jake Wharton Converter** | 1.0.0 | Bridges Kotlinx Serialization to Retrofit |
| **Gson** | 2.12.1 | Present but NOT used for API parsing |

### RetrofitClient.kt Analysis

```kotlin
// Location: api/RetrofitClient.kt

internal object RetrofitClient {
    private const val BASE_URL = "https://backend.appstorys.co/"
    private const val WEBSOCKET_BASE_URL = "https://users.appstorys.co/"

    private val json = Json {
        ignoreUnknownKeys = true      // Tolerates extra fields from server
        isLenient = true              // Allows malformed JSON
        coerceInputValues = true      // Converts null to default values
        explicitNulls = false         // Omits null values in serialization
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY  // ⚠️ LOGS FULL BODIES
                }
            )
            .build()
    }

    val apiService: ApiService by lazy { ... }
    val webSocketApiService: ApiService by lazy { ... }
}
```

### Why These Tools Were Chosen

| Choice | Reason | Tradeoff |
|--------|--------|----------|
| **Retrofit** | Industry standard, easy suspend function integration | Adds dependency size |
| **Kotlinx Serialization** | Compile-time safety, works with `@Serializable` data classes | Requires annotation processor |
| **BODY-level logging** | Full debug visibility during development | **SECURITY RISK in production** |
| **Two service instances** | Different base URLs for main API vs user API | Code duplication |

### What Could Be Improved

1. **❌ No timeout configuration** - Network calls can hang indefinitely
2. **❌ Logging level hardcoded to BODY** - Access tokens visible in logs
3. **❌ No certificate pinning** - Vulnerable to MITM
4. **❌ No connection pooling configuration** - Uses OkHttp defaults
5. **❌ No retry interceptor** - Single-shot requests

---

## 3️⃣ API Layer Structure

### ApiService.kt - Complete Endpoint Reference

```kotlin
// Location: api/ApiService.kt

internal interface ApiService {

    // ============ AUTHENTICATION ============
    @POST("{accountId}/validate-account")
    suspend fun validateAccount(
        @Path("accountId") accountId: String,
        @Body request: ValidateAccountRequest
    ): ValidateAccountResponse
    // Purpose: Exchange app credentials for access token
    // Request: ValidateAccountRequest(app_id, account_id, user_id)
    // Response: ValidateAccountResponse(access_token)

    // ============ CAMPAIGN ELIGIBILITY ============
    @POST("v2/{accountId}/track-user-res")
    suspend fun getEligibleCampaigns(
        @Path("accountId") accountId: String,
        @Header("Authorization") token: String,
        @Body request: TrackUserWebSocketRequest
    ): EligibleCampaignsResponse
    // Purpose: Get list of eligible campaign IDs for current screen/user
    // Request: TrackUserWebSocketRequest(user_id, screenName, silentUpdate)
    // Response: EligibleCampaignsResponse(eligibleCampaignList, variants, personalization_data)

    // ============ CAMPAIGN DATA LOADING ============
    @POST("load-campaign-data")
    suspend fun loadMissingCampaigns(
        @Header("Authorization") token: String,
        @Body campaignIds: List<String>
    ): List<Campaign>
    // Purpose: Fetch full campaign objects for IDs not in cache
    // Request: List<String> of campaign IDs
    // Response: List<Campaign> with full details

    // ============ USER MANAGEMENT ============
    @POST("reconcile-anonymous-user")
    suspend fun reconcileAnonymousUser(
        @Header("Authorization") token: String,
        @Body request: ReconcileUserRequest
    ): Response<Unit>
    // Purpose: Link anonymous user data to identified user

    @POST("update-user-atr")
    suspend fun updateUserProperties(
        @Header("Authorization") token: String,
        @Body request: UpdateUserPropertiesRequest
    ): Response<Unit>
    // Purpose: Set user attributes for segmentation

    // ============ FEEDBACK CAPTURE ============
    @POST("api/v1/campaigns/capture-csat-response/")
    suspend fun sendCSATResponse(
        @Header("Authorization") token: String,
        @Body request: CsatFeedbackPostRequest
    )
    // Purpose: Submit customer satisfaction rating

    @POST("api/v1/campaigns/reel-like/")
    suspend fun sendReelLikeStatus(
        @Header("Authorization") token: String,
        @Body request: ReelStatusRequest
    )
    // Purpose: Track reel like/unlike actions

    // ============ ELEMENT IDENTIFICATION ============
    @POST("api/v2/appinfo/identify-positions/")
    suspend fun identifyPositions(
        @Header("Authorization") token: String,
        @Body request: IdentifyPositionsRequest
    ): Nullable
    // Purpose: Send widget position information

    @Multipart
    @POST("api/v1/appinfo/identify-elements/")
    suspend fun identifyTooltips(
        @Header("Authorization") token: String,
        @Part("screenName") screenName: RequestBody,
        @Part("user_id") user_id: RequestBody,
        @Part("children") children: RequestBody,
        @Part screenshot: MultipartBody.Part
    )
    // Purpose: Send screenshot + view hierarchy for tooltip targeting
}
```

### Authentication Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. App provides: app_id + account_id + user_id                 │
│           │                                                     │
│           ▼                                                     │
│  2. POST /validate-account                                      │
│           │                                                     │
│           ▼                                                     │
│  3. Server validates credentials                                │
│           │                                                     │
│           ▼                                                     │
│  4. Returns: access_token (Bearer token)                        │
│           │                                                     │
│           ▼                                                     │
│  5. All subsequent requests include:                            │
│      @Header("Authorization") token: "Bearer $accessToken"      │
│                                                                 │
│  ⚠️ NO REFRESH TOKEN MECHANISM EXISTS                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4️⃣ Request → Response Lifecycle (Deep Dive)

### Tracing: Fetching Screen Campaigns

```kotlin
// STEP 1: UI Trigger
// Location: Any screen in the host app
AppStorys.getScreenCampaigns("Home Screen", listOf("position_1", "position_2"))

// STEP 2: AppStorys.kt (lines 350-392)
fun getScreenCampaigns(screenName: String, positionList: List<String> = emptyList()) {
    campaignsJob?.cancel()  // Cancel any pending request
    campaignsJob = coroutineScope.launch {
        if (!checkIfInitialized()) return@launch
        
        // Clear previous campaigns
        if (currentScreen != screenName) {
            campaigns.emit(emptyList())
            currentScreen = screenName
        }
        
        // STEP 3: Call repository
        val (campaignsList, variants, personalizationResponse, isTestUser) = 
            repository.getScreenCampaignsData(accessToken, accountId, currentScreen, userId)
        
        // STEP 4: Emit to StateFlow
        campaignsList?.let { campaigns.emit(it) }
        campaignVariants.emit(variants ?: emptyList())
    }
}

// STEP 3: ApiRepository.kt (lines 224-380)
suspend fun getScreenCampaignsData(accessToken, accountId, screenName, userId): ScreenCampaignResult {
    return withContext(Dispatchers.IO) {
        // 3a: Get eligible campaign IDs
        val eligibleCampaignsResult = safeApiCall {
            webSocketApiService.getEligibleCampaigns(
                accountId = accountId,
                token = "Bearer $accessToken",
                request = TrackUserWebSocketRequest(screenName = screenName, user_id = userId)
            )
        }
        
        when (eligibleCampaignsResult) {
            is ApiResult.Success -> {
                val eligibleCampaigns = eligibleCampaignsResult.data.eligibleCampaignList
                
                // 3b: Fetch campaigns.json from CDN (with ETag caching)
                fetchCampaignsJson(accountId)
                
                // 3c: Check for missing campaigns
                val missingCampaignIds = eligibleCampaigns.filter { it !in cachedCampaignIds }
                if (missingCampaignIds.isNotEmpty()) {
                    webSocketApiService.loadMissingCampaigns(token, missingCampaignIds)
                }
                
                // 3d: Filter by screen + eligibility
                val filteredCampaigns = cachedCampaignsJson?.filter { 
                    campaign.id in eligibleCampaigns && 
                    campaign.screen?.equals(screenName) == true 
                }
                
                // 3e: Apply A/B variants
                filteredCampaigns.map { campaign ->
                    val variant = variants.find { it.id == campaign.id }
                    if (variant != null) extractVariantFromCampaign(campaign, variant.v_id)
                    else campaign
                }
            }
        }
    }
}

// STEP 4: UI Observes StateFlow
// Location: AppStorys.kt Composables (e.g., Floater(), CSAT(), etc.)
@Composable
fun Floater() {
    val campaignsData = campaigns.collectAsStateWithLifecycle()
    val campaign = campaignsData.value.firstOrNull { it.campaignType == "FLT" }
    // Render floater...
}
```

### Complete Call Chain

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                          COMPLETE CALL CHAIN                                    │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  Host App                                                                      │
│    └── AppStorys.getScreenCampaigns("DashboardScreen")                         │
│         └── coroutineScope.launch { ... }                                      │
│              └── repository.getScreenCampaignsData(...)                        │
│                   └── withContext(Dispatchers.IO) { ... }                      │
│                        ├── webSocketApiService.getEligibleCampaigns(...)       │
│                        │    └── Retrofit.execute()                             │
│                        │         └── OkHttp.newCall()                          │
│                        │              └── HttpLoggingInterceptor logs          │
│                        │                   └── Server: POST /track-user-res    │
│                        │                        └── JSON Response              │
│                        │                             └── Kotlinx deserialize   │
│                        │                                  └── EligibleCampaignsResponse
│                        │                                                       │
│                        ├── fetchCampaignsJson(accountId)                       │
│                        │    └── OkHttpClient (direct, NOT Retrofit)            │
│                        │         └── GET S3 CDN with If-None-Match             │
│                        │              └── 200: Parse & cache                   │
│                        │              └── 304: Use SharedPreferences cache     │
│                        │                                                       │
│                        └── ScreenCampaignResult(campaigns, variants, ...)      │
│                             └── campaigns.emit(filteredCampaigns)              │
│                                  └── MutableStateFlow<List<Campaign>>          │
│                                       └── Composable.collectAsStateWithLifecycle()
│                                            └── UI Recomposition                │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5️⃣ Repository Pattern

### ApiRepository.kt Responsibilities

| Responsibility | Implementation | Location |
|----------------|----------------|----------|
| **Token acquisition** | `getAccessToken()` | Lines 32-45 |
| **Campaign orchestration** | `getScreenCampaignsData()` | Lines 224-380 |
| **CDN caching logic** | `fetchCampaignsJson()` | Lines 73-160 |
| **A/B variant extraction** | `extractVariantFromCampaign()` | Lines 164-220 |
| **Feedback submission** | `captureCSATResponse()`, `sendReelLikeStatus()` | Lines 382-408 |
| **Multipart uploads** | `tooltipIdentify()` | Lines 410-454 |

### Repository Pattern Analysis

**Good Practices:**
```kotlin
// ✅ Uses coroutine context switching
suspend fun getScreenCampaignsData(...): ScreenCampaignResult {
    return withContext(Dispatchers.IO) { ... }
}

// ✅ Wraps API calls in safe wrapper
when (val result = safeApiCall { apiService.someEndpoint() }) {
    is ApiResult.Success -> result.data
    is ApiResult.Error -> Log.e(...)
}

// ✅ Session-level caching flag
private var isCampaignsJsonFetchedThisSession = false
```

**Anti-Patterns:**
```kotlin
// ❌ Direct OkHttp usage inside repository (inconsistent with Retrofit)
val client = okhttp3.OkHttpClient()  // Line 90, fetchCampaignsJson()
val request = okhttp3.Request.Builder().url(campaignsJsonUrl).build()

// ❌ Hardcoded URLs (dev vs prod commented out)
val campaignsJsonUrl = "https://dev-cdn-campaign-appstorys.s3.ap-south-1.amazonaws.com/..."
// val campaignsJsonUrl = "https://s3.ap-south-1.amazonaws.com/cdn-campaigns.appstorys.com/..."

// ❌ Multiple services injected with unclear naming
private val apiService: ApiService,          // Main API
private val webSocketApiService: ApiService, // User API (NOT actually WebSocket)
```

### Caching Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       CAMPAIGNS.JSON CACHING                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Session Start                                                              │
│       │                                                                     │
│       ▼                                                                     │
│  isCampaignsJsonFetchedThisSession = false                                  │
│       │                                                                     │
│       ▼                                                                     │
│  First fetchCampaignsJson() call                                            │
│       │                                                                     │
│       ├── Has saved ETag? ──────────────────┐                               │
│       │       NO                            │ YES                           │
│       ▼                                     ▼                               │
│  GET without If-None-Match          GET with If-None-Match: "saved-etag"    │
│       │                                     │                               │
│       ▼                                     ▼                               │
│  Response 200                          Response 304 (Not Modified)          │
│       │                                     │                               │
│       ▼                                     ▼                               │
│  Parse JSON                            Load from SharedPreferences          │
│  Save to SharedPreferences                  │                               │
│  Save new ETag                              │                               │
│       │                                     │                               │
│       └─────────────┬───────────────────────┘                               │
│                     ▼                                                       │
│  cachedCampaignsJson = parsed campaigns                                     │
│  isCampaignsJsonFetchedThisSession = true                                   │
│       │                                                                     │
│       ▼                                                                     │
│  Subsequent calls skip network (use cached)                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Models & Mapping Strategy

### Model Architecture

This SDK uses a **SINGLE-LAYER MODEL** approach—the same data classes serve as:
- DTOs (Data Transfer Objects)
- Domain models
- UI models

```kotlin
// Location: api/Model.kt (1759 lines!)

@Keep
@Serializable
data class Campaign(
    val id: String?,
    @SerialName("campaign_type") val campaignType: String?,
    val details: CampaignDetails?,  // Polymorphic sealed class
    val position: String?,
    val screen: String?,
    @SerialName("trigger_event") val triggerEvent: String?
)

// Sealed class for type-safe campaign details
@Serializable
sealed class CampaignDetails

@Serializable
data class BannerDetails(...) : CampaignDetails()
@Serializable
data class FloaterDetails(...) : CampaignDetails()
@Serializable
data class CSATDetails(...) : CampaignDetails()
// ... 12+ more types
```

### Polymorphic Deserialization

The SDK uses **custom serializers** to handle polymorphic `CampaignDetails`:

```kotlin
// Location: api/Serializers.kt

object CampaignDeserializer : KSerializer<Campaign> {
    override fun deserialize(decoder: Decoder): Campaign {
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val campaignType = element["campaign_type"]?.jsonPrimitive?.contentOrNull
        
        val details = when (campaignType) {
            "FLT" -> jsonDecoder.json.decodeFromJsonElement<FloaterDetails>(detailsElement)
            "CSAT" -> jsonDecoder.json.decodeFromJsonElement<CSATDetails>(detailsElement)
            "BAN" -> jsonDecoder.json.decodeFromJsonElement<BannerDetails>(detailsElement)
            "REL" -> jsonDecoder.json.decodeFromJsonElement<ReelsDetails>(detailsElement)
            "STR" -> StoriesDetails(jsonDecoder.json.decodeFromJsonElement<List<StoryGroup>>(detailsElement))
            // ... 10+ more types
            else -> null
        }
        
        return Campaign(id, campaignType, details, ...)
    }
}
```

### Potential Risks

| Risk | Problem | Impact |
|------|---------|--------|
| **DTO leakage** | `Campaign` used directly in UI | Backend changes break UI |
| **Giant model file** | 1759 lines in Model.kt | Hard to navigate/maintain |
| **Nullable everything** | Most fields are `String?`, `Int?` | Null checks everywhere |
| **No validation** | No domain layer to validate data | Invalid data reaches UI |
| **Mixed concerns** | Styling mixed with content | Tight coupling |

### Recommended Separation (Not Currently Implemented)

```
Current:
  Server JSON → Campaign (DTO+Domain+UI)

Recommended:
  Server JSON → CampaignDto → Campaign (Domain) → CampaignUiState (UI)
```

---

## 7️⃣ Coroutines & Flow Usage

### Threading Strategy

```kotlin
// Location: AppStorys.kt

// Global coroutine scope (survives activity recreation)
private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Campaign job with cancellation
private var campaignsJob: Job? = null

fun getScreenCampaigns(screenName: String, positionList: List<String>) {
    campaignsJob?.cancel()  // Cancel previous
    campaignsJob = coroutineScope.launch {
        ensureActive()  // Check cancellation
        // ... work
    }
}
```

### StateFlow Usage

```kotlin
// Location: AppStorys.kt

// Hot streams for UI state
private val campaigns = MutableStateFlow<List<Campaign>>(emptyList())
private val disabledCampaigns = MutableStateFlow<List<String>>(emptyList())
private val impressions = MutableStateFlow<List<String>>(emptyList())
val tooltipTargetView = MutableStateFlow<Tooltip?>(null)

// UI observes with lifecycle awareness
@Composable
fun Floater() {
    val campaignsData = campaigns.collectAsStateWithLifecycle()  // ✅ Lifecycle-safe
    // ...
}
```

### Dispatcher Usage

| Operation | Dispatcher | Location |
|-----------|-----------|----------|
| API calls | `Dispatchers.IO` | `ApiRepository.kt` - `withContext(Dispatchers.IO)` |
| StateFlow updates | `Dispatchers.IO` | `AppStorys.kt` - via coroutineScope |
| Event tracking | `Dispatchers.IO` | `trackEvents()` - via coroutineScope |

### Cancellation Handling

```kotlin
// ✅ Good: Job cancellation on screen change
campaignsJob?.cancel()
campaignsJob = coroutineScope.launch { ... }

// ✅ Good: ensureActive() checks
ensureActive()  // Throws CancellationException if cancelled

// ⚠️ Potential issue: No structured concurrency
// The coroutineScope lives forever (object-level)
private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

### Lifecycle Observer

```kotlin
// Location: AppStorys.kt initialize()

ProcessLifecycleOwner.get().lifecycle.addObserver(
    object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (sdkState == AppStorysSdkState.Paused && currentScreen.isNotBlank()) {
                sdkState = AppStorysSdkState.Initialized
                getScreenCampaigns(currentScreen, emptyList())  // Refetch on resume
            }
        }
        
        override fun onStop(owner: LifecycleOwner) {
            sdkState = AppStorysSdkState.Paused
            campaigns.update { emptyList() }  // Clear campaigns
            campaignsJob?.cancel()            // Cancel pending work
        }
    }
)
```

---

## 8️⃣ Error Handling Strategy

### ApiResult Sealed Class

```kotlin
// Location: api/ApiService.kt

internal sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

internal suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: HttpException) {
        ApiResult.Error(e.message ?: "Unknown error", e.code())
    } catch (e: IOException) {
        ApiResult.Error("Network error. Please check your internet connection.")
    } catch (e: Exception) {
        ApiResult.Error("Unexpected error occurred.")
    }
}
```

### Error Propagation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ERROR PROPAGATION                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  API Call                                                                   │
│    │                                                                        │
│    ▼                                                                        │
│  safeApiCall { ... }                                                        │
│    │                                                                        │
│    ├── Success ────────────────────────────────────────────────┐            │
│    │                                                           │            │
│    ├── HttpException ──────┬─────────────────────────────────┐ │            │
│    │                       │                                 │ │            │
│    ├── IOException ────────┤ ApiResult.Error(message, code) │ │            │
│    │                       │                                 │ │            │
│    └── Exception ──────────┘                                 │ │            │
│                                                              │ │            │
│    ┌─────────────────────────────────────────────────────────┘ │            │
│    │                                                           │            │
│    ▼                                                           ▼            │
│  Repository handles error                              Return data          │
│    │                                                           │            │
│    ▼                                                           │            │
│  Log.e("...", error.message)                                   │            │
│  Return fallback (null, emptyList())                           │            │
│    │                                                           │            │
│    └───────────────────────────────────────────────────────────┘            │
│                            │                                                │
│                            ▼                                                │
│                   AppStorys object                                          │
│                   campaigns.emit(result ?: emptyList())                     │
│                            │                                                │
│                            ▼                                                │
│                   UI receives empty state (silent failure)                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### What's Missing

| Feature | Status | Impact |
|---------|--------|--------|
| **Loading state** | ❌ Not exposed to UI | Users don't see loading indicator |
| **Error state** | ❌ Not exposed to UI | Silent failures |
| **Retry logic** | ❌ Not implemented | Single-shot requests |
| **Timeout handling** | ❌ No timeouts configured | Hangs indefinitely |
| **Offline detection** | ❌ Not implemented | No graceful degradation |

---

## 9️⃣ Caching & Persistence

### Caching Mechanisms

| Layer | Storage | Data | TTL |
|-------|---------|------|-----|
| **Session cache** | In-memory | `cachedCampaignsJson` | Until app stop |
| **Persistent cache** | SharedPreferences | Campaigns JSON + ETag | Forever |
| **Scratched campaigns** | SharedPreferences | List of scratched IDs | Forever |
| **Liked reels** | SharedPreferences | List of liked reel IDs | Forever |
| **User ID** | SharedPreferences | Anonymous/identified ID | Forever |

### SharedPreferences Usage

```kotlin
// Location: ApiRepository.kt
private val sharedPreferences = context.getSharedPreferences("appversal_campaigns", Context.MODE_PRIVATE)

companion object {
    private const val PREF_CAMPAIGNS_JSON = "campaigns_json"
    private const val PREF_ETAG = "campaigns_etag"
}

// Location: AppStorys.kt
val prefs = context.getSharedPreferences("AppStory", Context.MODE_PRIVATE)
prefs.getString(PREFS_USER_ID, null)
prefs.getBoolean(PREFS_IS_ANONYMOUS, true)
```

### ETag-Based Caching

```kotlin
// Location: ApiRepository.kt fetchCampaignsJson()

val savedETag = sharedPreferences.getString(PREF_ETAG, null)

if (savedETag != null) {
    requestBuilder.addHeader("If-None-Match", savedETag)
}

when (response.code) {
    200 -> {
        // New data - parse and save
        val newETag = response.header("ETag")
        sharedPreferences.edit {
            putString(PREF_CAMPAIGNS_JSON, jsonString)
            putString(PREF_ETAG, newETag)
        }
    }
    304 -> {
        // Not modified - use cache
        val cachedJsonString = sharedPreferences.getString(PREF_CAMPAIGNS_JSON, null)
        cachedCampaignsJson = SdkJson.decodeFromString(cachedJsonString)
    }
}
```

### What's Missing

| Feature | Status | Recommendation |
|---------|--------|----------------|
| **Room DB** | ❌ Not used | Use for complex queries |
| **DataStore** | ❌ Not used | Replace SharedPreferences |
| **Memory LRU cache** | ❌ Not used | Cache parsed campaigns |
| **Image caching** | Coil default | OK for images |
| **Video caching** | Custom `VideoCache` | Exists but limited |

---

## 🔟 Authentication & Security

### Token Handling

```kotlin
// Location: AppStorys.kt
private var accessToken = ""  // ⚠️ Stored in memory, plain text

// Token acquisition
val accessToken = repository.getAccessToken(appId, accountId, this@AppStorys.userId)
this@AppStorys.accessToken = accessToken

// Token usage (every request)
@Header("Authorization") token: String  // "Bearer $accessToken"
```

### Security Analysis

| Aspect | Implementation | Risk Level |
|--------|----------------|------------|
| **Token storage** | Plain memory variable | Medium - lost on process death |
| **Token transmission** | HTTPS only | Low |
| **Token refresh** | ❌ Not implemented | High - no handling for expired tokens |
| **Logging** | BODY level logs | **CRITICAL** - tokens in logcat |
| **Certificate pinning** | ❌ Not implemented | Medium - MITM possible |
| **User ID storage** | SharedPreferences plain | Low - non-sensitive |

### Critical Vulnerability

```kotlin
// Location: RetrofitClient.kt

private val client by lazy {
    OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY  // ⚠️ LOGS ACCESS TOKENS!
            }
        )
        .build()
}
```

**Fix Required:**
```kotlin
.addInterceptor(
    HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) 
            HttpLoggingInterceptor.Level.BODY 
        else 
            HttpLoggingInterceptor.Level.NONE
    }
)
```

---

## 1️⃣1️⃣ Performance Analysis

### Identified Issues

| Issue | Location | Impact |
|-------|----------|--------|
| **Full JSON parsing every fetch** | `fetchCampaignsJson()` | CPU spike |
| **No pagination** | `campaigns.json` contains ALL campaigns | Memory bloat |
| **Blocking main thread** | Some SharedPreferences `commit()` | UI jank |
| **Multiple OkHttp instances** | Retrofit + direct OkHttp | Connection overhead |
| **No image prefetching** | Campaign images loaded on demand | Visible loading |

### Unnecessary API Calls

```kotlin
// Location: AppStorys.kt initialize()

// Auto-fetches Home Screen on init
if (campaignsJob?.isActive != true) {
    getScreenCampaigns("Home Screen", emptyList())  // ⚠️ May not be needed
}
```

### Event Tracking - Direct OkHttp

```kotlin
// Location: AppStorys.kt trackEvents()

// Uses separate OkHttp client, not Retrofit!
val client = OkHttpClient()  // ⚠️ New instance every call
val request = Request.Builder()
    .url("https://tracking.appstorys.co/capture-event")
    .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
    .addHeader("Authorization", "Bearer $accessToken")
    .build()

val response = client.newCall(request).execute()  // ⚠️ Blocking call in coroutine
```

### Recommendations

1. **Reuse OkHttp client** - Move to `RetrofitClient`
2. **Add pagination** - Don't fetch all campaigns at once
3. **Implement prefetching** - Preload images for visible campaigns
4. **Use `apply()` not `commit()`** - For SharedPreferences writes
5. **Add request coalescing** - Deduplicate rapid screen changes

---

## 1️⃣2️⃣ Dependency Injection

### Current State: Manual Instantiation

```kotlin
// Location: AppStorys.kt

// Singleton services created at object level
private val apiService = RetrofitClient.apiService
private val webSocketService = RetrofitClient.webSocketApiService

// Repository created during initialize()
this.repository = ApiRepository(context, apiService, webSocketService) {
    currentScreen
}
```

### No DI Framework Used

The SDK uses **object singletons** instead of Hilt/Koin/Dagger:

```kotlin
// Location: RetrofitClient.kt
internal object RetrofitClient {
    val apiService: ApiService by lazy { ... }
    val webSocketApiService: ApiService by lazy { ... }
}

// Location: AppStorys.kt
object AppStorys {
    // All state lives here
}
```

### Testing Implications

| Aspect | Current | With DI |
|--------|---------|---------|
| **Mock APIs** | Impossible without modifying source | Easy with interface injection |
| **Fake repository** | Impossible | Easy |
| **Scope control** | Global singletons | Activity/Fragment scoped |

### Recommended Structure (Not Implemented)

```kotlin
// With Hilt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideApiService(): ApiService = RetrofitClient.apiService
    
    @Provides @Singleton
    fun provideRepository(api: ApiService): ApiRepository = ApiRepository(api)
}
```

---

## 1️⃣3️⃣ Testing Strategy

### Current State: No Tests Found

The test directories exist but appear empty:

```
app/appstorys/src/test/java/          # Unit tests (empty)
app/appstorys/src/androidTest/java/   # Instrumented tests (empty)
```

### How to Test This Network Layer

**1. Unit Testing Repository:**
```kotlin
class ApiRepositoryTest {
    private val mockApiService = mockk<ApiService>()
    
    @Test
    fun `getAccessToken returns token on success`() = runTest {
        coEvery { 
            mockApiService.validateAccount(any(), any()) 
        } returns ValidateAccountResponse(access_token = "test-token")
        
        val repo = ApiRepository(mockContext, mockApiService, mockWsService) { "screen" }
        val result = repo.getAccessToken("app", "account", "user")
        
        assertEquals("test-token", result)
    }
}
```

**2. Integration Testing with MockWebServer:**
```kotlin
class ApiServiceIntegrationTest {
    @get:Rule
    val mockWebServer = MockWebServer()
    
    @Test
    fun `validateAccount parses response correctly`() = runTest {
        mockWebServer.enqueue(MockResponse()
            .setBody("""{"access_token": "abc123"}""")
            .setResponseCode(200))
        
        val api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .build()
            .create(ApiService::class.java)
        
        val response = api.validateAccount("acc", ValidateAccountRequest(...))
        assertEquals("abc123", response.access_token)
    }
}
```

---

## 1️⃣4️⃣ Architecture Quality Review

### Strengths ✅

| Aspect | Why It's Good |
|--------|---------------|
| **Sealed class for details** | Type-safe campaign polymorphism |
| **StateFlow for state** | Modern reactive approach |
| **ETag caching** | Efficient bandwidth usage |
| **Custom serializers** | Handles complex JSON structures |
| **Lifecycle awareness** | ProcessLifecycleOwner integration |
| **Session-level cache flag** | Prevents redundant fetches |

### Weaknesses ❌

| Issue | Why It's Bad | Severity |
|-------|--------------|----------|
| **God object (AppStorys)** | 2453 lines, does everything | High |
| **No separation of concerns** | DTOs = Domain = UI | High |
| **Hardcoded URLs** | Dev URL checked in | Medium |
| **BODY-level logging** | Security risk | Critical |
| **No error UI state** | Silent failures | Medium |
| **Mixed API clients** | Retrofit + raw OkHttp | Low |
| **No DI** | Hard to test | Medium |
| **Giant Model.kt** | 1759 lines | Medium |

### Refactoring Suggestions

1. **Split AppStorys.kt:**
   - `AppStorysCore.kt` - Initialization, state
   - `AppStorysCampaigns.kt` - Campaign logic
   - `AppStorysUI.kt` - Composables
   - `AppStorysTracking.kt` - Event tracking

2. **Introduce ViewModel layer:**
   ```kotlin
   class CampaignViewModel(private val repository: ApiRepository) : ViewModel() {
       val campaigns: StateFlow<UiState<List<Campaign>>>
       fun loadScreen(screenName: String)
   }
   ```

3. **Create domain models:**
   ```kotlin
   // api/dto/CampaignDto.kt - Server contract
   // domain/Campaign.kt - Business logic
   // ui/CampaignUiState.kt - UI state
   ```

---

## 1️⃣5️⃣ Diagrams

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DATA FLOW                                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌──────────┐     ┌──────────────┐     ┌───────────────┐     ┌─────────────┐  │
│   │  Host    │     │  AppStorys   │     │ ApiRepository │     │  Retrofit   │  │
│   │   App    │────▶│   Object     │────▶│               │────▶│  ApiService │  │
│   └──────────┘     └──────────────┘     └───────────────┘     └─────────────┘  │
│        │                   │                    │                    │          │
│        │                   │                    │                    │          │
│        │                   ▼                    │                    ▼          │
│        │           ┌──────────────┐             │            ┌─────────────┐   │
│        │           │ MutableState │             │            │   OkHttp    │   │
│        │           │    Flow      │             │            │   Client    │   │
│        │           └──────────────┘             │            └─────────────┘   │
│        │                   │                    │                    │          │
│        │                   │                    │                    ▼          │
│        │                   ▼                    │            ┌─────────────┐   │
│   ┌──────────┐     ┌──────────────┐             │            │   Server    │   │
│   │Composable│◀────│collectAsState│             │            │   (REST)    │   │
│   │   UI     │     │WithLifecycle │             │            └─────────────┘   │
│   └──────────┘     └──────────────┘             │                    │          │
│                                                 │                    ▼          │
│                                          ┌──────────────┐    ┌─────────────┐   │
│                                          │SharedPrefs   │◀───│   JSON      │   │
│                                          │(Cache+ETag)  │    │  Response   │   │
│                                          └──────────────┘    └─────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### API Dependency Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            API DEPENDENCIES                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│                          ┌─────────────────┐                                    │
│                          │   AppStorys     │                                    │
│                          │    Object       │                                    │
│                          └────────┬────────┘                                    │
│                                   │                                             │
│                    ┌──────────────┼──────────────┐                              │
│                    ▼              ▼              ▼                              │
│            ┌───────────┐  ┌───────────┐  ┌────────────┐                         │
│            │ apiService│  │webSocket  │  │  Direct    │                         │
│            │(Retrofit) │  │ApiService │  │  OkHttp    │                         │
│            └─────┬─────┘  └─────┬─────┘  └──────┬─────┘                         │
│                  │              │               │                               │
│      ┌───────────┴───────────┐  │               │                               │
│      ▼           ▼           ▼  ▼               ▼                               │
│  ┌────────┐ ┌────────┐ ┌────────────┐    ┌────────────┐                         │
│  │identify│ │sendCSAT│ │validate    │    │campaigns   │                         │
│  │Positions│ │Response│ │Account     │    │.json (S3)  │                         │
│  └────────┘ └────────┘ └────────────┘    └────────────┘                         │
│                                                                                 │
│  ┌────────┐ ┌────────┐ ┌────────────┐    ┌────────────┐                         │
│  │sendReel│ │identify│ │getEligible │    │capture     │                         │
│  │LikeStat│ │Tooltips│ │Campaigns   │    │Event       │                         │
│  └────────┘ └────────┘ └────────────┘    │(tracking)  │                         │
│                                          └────────────┘                         │
│                        ┌────────────┐                                           │
│                        │loadMissing │                                           │
│                        │Campaigns   │                                           │
│                        └────────────┘                                           │
│                                                                                 │
│                        ┌────────────┐                                           │
│                        │updateUser  │                                           │
│                        │Properties  │                                           │
│                        └────────────┘                                           │
│                                                                                 │
│                        ┌────────────┐                                           │
│                        │reconcile   │                                           │
│                        │Anonymous   │                                           │
│                        └────────────┘                                           │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Layer Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             LAYER ARCHITECTURE                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                            UI LAYER                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  @Composable functions in AppStorys.kt                              │  │  │
│  │  │  - overlayElements(), CSAT(), Floater(), Pip(), etc.                │  │  │
│  │  │  - Observes: campaigns.collectAsStateWithLifecycle()                │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  UI Components (ui/ folder)                                         │  │  │
│  │  │  - CsatDialog, OverlayFloater, PipVideo, ReelsScreen, etc.          │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                      ▲                                          │
│                                      │ StateFlow                                │
│                                      │                                          │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          STATE LAYER                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  AppStorys object (AppStorys.kt)                                    │  │  │
│  │  │  - campaigns: MutableStateFlow<List<Campaign>>                      │  │  │
│  │  │  - accessToken, currentScreen, userId                               │  │  │
│  │  │  - trackEvents(), setUserProperties(), setUserId()                  │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                      ▲                                          │
│                                      │ ScreenCampaignResult                     │
│                                      │                                          │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                        REPOSITORY LAYER                                   │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  ApiRepository (api/ApiRepository.kt)                               │  │  │
│  │  │  - getAccessToken(), getScreenCampaignsData()                       │  │  │
│  │  │  - fetchCampaignsJson(), extractVariantFromCampaign()               │  │  │
│  │  │  - captureCSATResponse(), sendReelLikeStatus()                      │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                      ▲                                          │
│                                      │ ApiResult<T>                             │
│                                      │                                          │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                         NETWORK LAYER                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  ApiService (api/ApiService.kt) - Retrofit interface                │  │  │
│  │  │  RetrofitClient (api/RetrofitClient.kt) - OkHttp + Json config      │  │  │
│  │  │  safeApiCall() wrapper                                              │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                      ▲                                          │
│                                      │ HTTP/HTTPS                               │
│                                      │                                          │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          DATA LAYER                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  Models (api/Model.kt) - 1759 lines of @Serializable data classes   │  │  │
│  │  │  Serializers (api/Serializers.kt) - Custom JSON deserializers       │  │  │
│  │  │  SdkJson (utils/SdkJson.kt) - Json configuration                    │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 1️⃣6️⃣ Learning Takeaways

### From Studying This App's API Layer I Can Learn:

**✅ Patterns & Best Practices:**

1. **Sealed class for API results** - `ApiResult<T>` provides type-safe error handling
2. **ETag caching pattern** - Efficient bandwidth with HTTP 304 support
3. **Polymorphic JSON handling** - Custom serializers for varying `details` types
4. **Session-level cache flags** - `isCampaignsJsonFetchedThisSession` prevents redundant calls
5. **ProcessLifecycleOwner** - App-level lifecycle awareness for SDK state
6. **StateFlow for UI state** - Modern reactive approach with `collectAsStateWithLifecycle`
7. **A/B variant extraction** - Server-driven experimentation via `variants` array

**⚠️ Common Mistakes to Avoid:**

1. **God objects** - AppStorys.kt at 2453 lines does too much
2. **Hardcoded environment URLs** - Dev URL checked into source
3. **BODY-level logging in production** - Security vulnerability
4. **No timeout configuration** - Requests can hang forever
5. **Mixed API clients** - Retrofit + raw OkHttp creates confusion
6. **Single-layer models** - DTOs leak to UI
7. **No dependency injection** - Impossible to unit test
8. **Silent failures** - Errors logged but not shown to user

**🎯 How Senior Engineers Design Networking:**

1. **Layered architecture** - Clear separation: UI → State → Repository → Network
2. **Offline-first design** - Cache first, then validate with server
3. **Error propagation** - Errors should reach UI with retry options
4. **Testability** - Interfaces for everything, DI for mocking
5. **Security** - Never log sensitive data, use BuildConfig for environments
6. **Performance** - Pagination, prefetching, connection pooling
7. **Observability** - Structured logging, crash reporting, analytics

---

## Quick Reference Card

### File Locations

| File | Purpose | Lines |
|------|---------|-------|
| `api/ApiService.kt` | Retrofit interface | 93 |
| `api/ApiRepository.kt` | Business logic | 454 |
| `api/RetrofitClient.kt` | Network config | 50 |
| `api/Model.kt` | Data classes | 1759 |
| `api/Serializers.kt` | Custom deserializers | 343 |
| `AppStorys.kt` | SDK entry point | 2453 |
| `utils/SdkJson.kt` | JSON configuration | 46 |

### Campaign Type Codes

| Code | Type | Details Class |
|------|------|---------------|
| `BAN` | Banner | `BannerDetails` |
| `FLT` | Floater | `FloaterDetails` |
| `CSAT` | Customer Satisfaction | `CSATDetails` |
| `WID` | Widget | `WidgetDetails` |
| `REL` | Reels | `ReelsDetails` |
| `TTP` | Tooltips | `TooltipsDetails` |
| `PIP` | Picture-in-Picture | `PipDetails` |
| `BTS` | Bottom Sheet | `BottomSheetDetails` |
| `SUR` | Survey | `SurveyDetails` |
| `MOD` | Modal | `ModalDetails` |
| `STR` | Stories | `StoriesDetails` |
| `SCRT` | Scratch Card | `ScratchCardDetails` |
| `STW` | Spin the Wheel | `SpinTheWheelDetails` |
| `MIL` | Milestone | `MilestoneDetails` |

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/{accountId}/validate-account` | POST | Get access token |
| `/v2/{accountId}/track-user-res` | POST | Get eligible campaigns |
| `/load-campaign-data` | POST | Load missing campaigns |
| `/reconcile-anonymous-user` | POST | Link anonymous to identified |
| `/update-user-atr` | POST | Set user properties |
| `/api/v1/campaigns/capture-csat-response/` | POST | Submit CSAT feedback |
| `/api/v1/campaigns/reel-like/` | POST | Track reel likes |
| `/api/v1/appinfo/identify-elements/` | POST (Multipart) | Send tooltip screenshots |
