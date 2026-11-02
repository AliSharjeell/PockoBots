# Floating bot and live webapp preview

The top-right floating-bot button in a bot's chat pins that bot over other apps.
Android's **Display over other apps** permission is required. Tap the button
again, the red close button, or Dismiss in the notification to remove it.
Closing the pill pauses active Mobile Use; ordinary coding runs can continue.

While idle the pinned bot becomes a draggable avatar circle. Tap it to expand
its panel and message input. While working it shows its animated avatar,
elapsed time, step count and live progress. The expanded input is kept mounted
while polling updates the surrounding progress, so drafts and focus survive
new tokens. The keyboard moves the panel up and the panel restores its previous
position when the keyboard closes. Long questions remain readable in the
scrolling area without pushing the composer off the panel.

Messages go to the same bot's normal conversation. BYOK runs can receive live
updates at a tool boundary, including an active Mobile Use run. Subscription
CLI runs currently queue follow-ups after the active run rather than aborting
or replacing it. A pending Mobile Use question uses the answer endpoint instead.
Pinned mode is restored if Android restarts its service; force-stop and Android
battery restrictions can still stop an app's foreground services.

For “build my app and take me there,” bots can start and verify the server,
then use `open_preview`. Android opens the URL directly in a dedicated webapp
preview without a screen-control model navigating Chrome. The floating bot
stays available for follow-up changes. Dev-server hot reload updates the page;
`open_preview` with `refresh: true` reloads the existing preview if needed.
Regular preview links in chat continue to open normally.

The preview accepts HTTP/HTTPS only, has no native JavaScript bridge and denies
file/content access. Requests and launch receipts are persisted in app state;
a receipt confirms the URL handoff, not successful visual rendering. A locked
device defers opening, and queued requests expire after two minutes. The Android
runtime must be running to receive requests.

The pill uses a translucent glass finish with highlights and rounded edges.
This is a native overlay treatment, not a promise of cross-app lens refraction
on every Android version. No accessibility permission is granted by merely
pinning a bot or opening a preview. Visible Mobile Use retains its existing
task authorization and accessibility requirements.
