# Android Remote Lab Agent

Transparent Android agent for a personal remote-management lab.

## Scope
The agent supports one command only:
- OPEN_WHATSAPP

It does not read WhatsApp chats, databases, tokens, notifications, or credentials. It does not provide hidden screen capture, keylogging, microphone, or camera access.

## Build
Use the GitHub Actions workflow:
Actions -> Build Android Agent APK -> Run workflow.

The workflow uploads a debug APK as an artifact.

## Configure on device
- API URL: https://android-remote-lab.netlify.app
- Device token: set the same secret value configured on the Android Remote Lab Netlify site.

Keep the Android app open during testing.
