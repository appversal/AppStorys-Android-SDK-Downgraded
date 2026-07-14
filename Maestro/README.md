# Maestro Flows — AppStorys SDK QA

Two flow families live here.

## 1. Campaign-type flows (`<type>_*.yaml`)

Registered per-type in `AppStorys-QA\pipeline-config.json → maestroFlows`,
run automatically by Layer 4 for whichever campaign type the pipeline
detects. Smoke / interaction / dismiss / back-press per type.

### PIP (trigger = On Launch)

PIP renders on cold start — no trigger event. (An older "Login Event"
button gated it during testing; that gate is dead and removed from the
PIP flows.) The cross/expand controls carry contentDescriptions
(`"Close"`, `"Maximize"`, `"Minimize"`) so Maestro can match them by
`text`.

| Flow | Proves | Events fired |
|---|---|---|
| `pip_smoke.yaml` | card renders on launch | `viewed{is_small_video:true}` |
| `pip_expand.yaml` | small-card cross dismisses PIP | `viewed{true}` |
| `pip_lifecycle.yaml` | render → expand → CTA (the L5/L6 flow) | `viewed{true}` → `viewed{false}` → `clicked{false}` |
| `pip_interaction.yaml` | expand → back collapses (not app) | `viewed{true}` → `viewed{false}` |

**Lesson from the `[dynamic] PIP_smoke` failure:** it asserted `"Close"`
with no bounded wait, firing before cold-start render finished →
deterministic 100% fail. Every PIP flow must gate the first assert
behind `extendedWaitUntil visible:"Close" timeout:12000` (covers the
eligibility call + campaigns.json fetch + render). It was never a
missing-contentDescription bug.

## 2. Behavior flows (`bhv_*.yaml`) — dashboard-logic tests

Each one verifies ONE dashboard setting end-to-end. They are **not** run
automatically by Layer 4 because each needs a specific dashboard
configuration first (noted at the top of every file). Run manually:

```
maestro test Maestro\bhv_trigger_onlaunch.yaml
maestro test -e TRIGGER_BUTTON="Purchased Event" Maestro\bhv_trigger_event.yaml
```

| Flow | Dashboard setting under test | What proves a pass |
|---|---|---|
| `bhv_trigger_onlaunch.yaml` | Trigger → On Launch | campaign appears with zero interaction |
| `bhv_trigger_event.yaml` | Trigger → Event-Based | hidden before event, appears after event button tap |
| `bhv_trigger_via_campaign.yaml` | Trigger → via AppStorys Campaigns | campaign B appears only after campaign A's CTA click |
| `backpress_campaign.yaml` | Trigger → back_press event | 1st back consumed + campaign shows; 2nd back passes through |
| `bhv_freq_once.yaml` | Frequency → Impression → Only once | shows on 1st launch, suppressed after restart |
| `bhv_freq_unlimited.yaml` | Frequency → Impression → Unlimited | shows again after restart |
| `bhv_freq_cooldown.yaml` | Frequency → Impression → Cooldown | suppressed on immediate restart |
| `bhv_freq_once_new_user.yaml` | "Only once" is per-user | clearState → new user id → shows again |
| `bhv_str_quiz_explanation.yaml` | Quiz → Show explanation ON | explanation text appears after answering |
| `bhv_str_poll_results_off.yaml` | Poll → Show results OFF | no `NN%` figures after voting |
| `bhv_str_poll_results_on.yaml` | Poll → Show results ON | `NN%` figures appear after voting |
| `bhv_str_state_save.yaml` | Interactive state persistence | vote → close → reopen → results still shown without re-voting |
| `bhv_str_swipeup_first.yaml` | Swipe-up CTA redirect | ONE swipe leaves the viewer (not only the second) |
| `bhv_str_countdown_ended.yaml` | Countdown at zero | ended countdown shows no negative time |

All six `bhv_str_*` flows require their interaction on the **first
displayed slide** (slides auto-advance every `slideShowTime` seconds) and
each header documents the exact dashboard setup. Dismiss is always
swipe-down, never the back key (SDK bug #6: BackHandler crash when no
back_press campaign is armed).

## How the SDK actually decides (for flow authors)

1. **Eligibility (frequency, scheduling, audience) = backend.**
   `track-user-res` returns `eligibleCampaignList` per user id. If a
   campaign is missing there, the SDK never renders it. All frequency
   flows therefore restart the app (`stopApp: true`) to force a fresh
   eligibility call.
2. **Trigger = SDK-side** (`TriggerEventMatcher`):
   - `trigger_event` null/empty → On Launch
   - string/object with event name → held until `trackEvents(name)`
   - `"viaAppStorys"` → held until another campaign's CTA click emits
     `viaAppStorys<campaignId>`
   - `event_config` entry `backPress: true` → held until hardware back
     (SDK consumes that press)
3. **User id lives in SharedPreferences** (`"AppStory"`), so
   `clearState: false` keeps the same user across restarts;
   `clearState: true` creates a new user (resets per-user frequency).
4. **In-memory state** (`tooltipViewed`, `impressions`,
   `backPressCampaignConsumed`) survives `am start` but not
   `stopApp: true` — never test frequency without a full restart.

## Timing rules

No blind sleeps. `extendedWaitUntil … timeout: 15000` covers the
eligibility call + campaigns.json fetch on cold start. The idiom

```yaml
- extendedWaitUntil:
    visible:
      text: "___never_exists___"
    timeout: 4000
    optional: true
```

is a bounded wait (used to let impression calls flush before killing
the process) that can never fail the flow.

Screenshots land in `Maestro\screenshots\`.
