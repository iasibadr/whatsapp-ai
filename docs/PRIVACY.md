# Privacy and permissions

The APK must contain application code and generic configuration only. Never distribute private app storage, API credentials, ADB identities, contacts, call histories, recordings or mission exports with it.

## Data flow

The app sends call audio and mission instructions to the configured OpenAI service during AI operation. A local relay does not mean that audio processing stays offline. Credentials are encrypted with AES-GCM and an Android Keystore key in KeyVault; this does not protect against every compromised-device threat. Message and history features store app data locally; review and delete it using the app. Android backup is disabled in both manifests.

WhatsApp AI requests READ_CONTACTS for contact selection and contact presentation. Selected contact data and imported mission documents can be sensitive.

## Permissions and privileged boundary

Internet access supports model requests. Audio, phone, wake-lock, notification and foreground-service permissions support call handling and a visible running session. Biometric permission supports protected screens. The local wireless-debugging association grants a shell-level capability; it is more powerful than normal microphone access. Pair only your own device through its deliberate setup UI and revoke access when no longer needed.

The exported bridge provider and Binder endpoints must enforce caller UID and method checks. The presence of these checks is not a penetration-test certification. Do not expose ADB or relay control over a public network. Diagnostic logs may contain operational details; inspect and redact them before sharing.

## Consent and reporting

Inform participants before AI processing. Do not use the app for covert recording, impersonation or unsolicited automated calls. An AI tool call claiming consent is not independent proof of a human's consent. Respect participants' requests to stop.

Never publish a full bug report captured during a private call without reviewing it. Use synthetic names, fictional numbers and a controlled test participant.
