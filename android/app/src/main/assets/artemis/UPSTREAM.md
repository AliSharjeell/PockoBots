# Artemis components

Source: https://github.com/google/artemis
Pinned commit: 351ca8422f7b5b54e80a9c1ce03a222e02415b6b
License: Apache-2.0 (see LICENSE).

The native accessibility helper and Flash execution prompt are vendored here.
Pocket Agent adapts the helper lifecycle, authentication and task consent to run
inside its own APK. Its TypeScript task runner implements the Flash observe/act
contract using the existing BYOK adapters and native accessibility transport.
The desktop Python/ADB runner, Pro multi-agent graph, desktop console, video
recording, OCR/explorer and benchmark claims are not included in this port.

Modifications are recorded in Git and described in docs/MOBILE_USE.md.
