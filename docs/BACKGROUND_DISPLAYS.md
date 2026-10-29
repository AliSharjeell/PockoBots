# Background app displays: capability and limits

PockoBots currently operates Android apps on the visible phone display through
Artemis. A task that explicitly requires hidden native-app operation must not
silently fall back to controlling the owner's visible app.

## Three separate capabilities

| Capability | What it proves | What it does not prove |
| --- | --- | --- |
| Another task/window | An app can expose a second activity/task when it supports that | Another account or isolated app data |
| Work/clone profile or Private Space | The platform can maintain separate profile data/accounts on supported devices | Hidden display capture and independent input for an ordinary app |
| Virtual display | Content can render onto a second display/surface | Arbitrary installed apps, independent login state, screenshot access and input all work together |

The public [DisplayManager API](https://developer.android.com/reference/android/hardware/display/DisplayManager)
permits private, app-owned displays. Their ownership and window rules are
restricted by UID. Auto-mirroring and secure flags involve additional privileged
permissions or the appropriate consent-based projection flow. These are not
permissions that a standard app can simply turn on in its settings screen.

[ActivityOptions.setLaunchDisplayId](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int))
requests a display for an activity; it does not clone the package or guarantee
that a particular app accepts the launch. Android 11 added
[GestureDescription.Builder.setDisplayId](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder#setDisplayId(int));
that supplies a target for gestures, not an isolated app session or a complete
background-control transport.

## Implemented probe

AgentDisplayCapabilities.inspect(context) reports hiddenNativeAppControl=false,
isolatedAppAccounts=false and availableMode=foreground.

probeOwnDisplay(context, optionalPackageName, callback) creates a small private
presentation display, renders a PockoBots-owned marker, and checks ImageReader
pixels. It can check Android's activity-launch policy for an installed target
without launching that target. It releases the presentation, display, reader and
worker thread after the check. A returned probeDisplayId is diagnostic and has
already been released; it must never be used as an active automation target.

The following fields are separate evidence: ownDisplayCreated,
surfaceFrameReceived, ownContentVerified, targetLaunchPolicyAllows,
targetActivityLaunched and agentDisplayInputVerified. Only app-owned content is
tested. A true policy check must never be displayed as successful LinkedIn or
Instagram control. The probe never signs in, clones packages, changes profiles,
launches a third-party app, or grants itself privileges.

## Standalone app and optional companion

The standalone APK has no verified companion that launches isolated LinkedIn or
Instagram sessions, supplies their frames and routes independent input. Hidden
native-app operation remains unavailable until all of those steps are verified
on the actual device. Visible phone use remains available with Accessibility,
floating-window permissions and an unlocked screen.

A separately configured companion with shell/system privileges can support a
prototype. [scrcpy's virtual display documentation](https://github.com/Genymobile/scrcpy/blob/master/doc/virtual-display.md)
describes creating a display and starting an app there. scrcpy's ADB/shell server
has a different privilege model from this APK. Its successful launch would not
prove a separately authenticated copy of an app. A future companion also needs
owner-controlled enrollment, display-scoped capture/input, cleanup, account
isolation and a hard foreground/background boundary. It must not be presented as
installed or connected when no such companion exists.

## Validation

AgentDisplayCapabilitiesTest runs a short-lived probe and records its JSON in
private files/display-probe.json. It checks that capability claims remain false
for hidden arbitrary-app control, even when private app-owned rendering succeeds.
The test passed on the existing Pixel 10 Pro XL API 37 emulator in 4 KB mode
on 2026-10-29 (2.128 seconds), in the isolated QA application. Its measured result:

```json
{
  "secondaryActivityFeature": true,
  "ownDisplayCreated": true,
  "surfaceFrameReceived": true,
  "ownContentVerified": true,
  "targetPackage": "com.android.chrome",
  "targetLaunchIntentAvailable": true,
  "targetLaunchPolicyAllows": false,
  "targetActivityLaunched": false,
  "hiddenNativeAppControl": false,
  "isolatedAppAccounts": false,
  "agentDisplayInputVerified": false,
  "probeReleased": true
}
```

This confirms app-owned off-screen rendering on this emulator. Android's policy
check rejected Chrome on that private display. No third-party app was launched,
no input was injected, and no account/profile was cloned. The normal app's data
and model connection were not changed by this test. Physical-device results can
differ and require their own probe.

Use the existing Pixel development launcher for the experimental Intel emulator.
It flushes Android storage before a restart. Do not wipe the AVD or replace its
snapshot to test this feature. The project already records an earlier emulator
snapshot/data limitation in VERIFICATION.md; a display probe does not restore or
establish preservation of older emulator data.
