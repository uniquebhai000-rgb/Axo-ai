# AXO AI

ChatGPT-style Android assistant (Kotlin · Jetpack Compose · Material 3 · MVVM · Room) with a Node.js backend that keeps all AI API keys off the device.

```
axo-ai/
├─ backend/   Node.js API: auth, model proxy (SSE streaming), search, images
└─ android/   Android Studio project
```

## 1. Run the backend
```bash
cd backend
cp .env.example .env     # set JWT_SECRET and at least one provider key
npm install
npm start                # http://localhost:8787
```
- Models live in `backend/models.json`. A model is only shown in the app if its key (`keyEnv`) is set. Add or remove models by editing that file and restarting; no app rebuild needed.
- Web search needs `TAVILY_API_KEY`; image generation needs `OPENAI_API_KEY`. Without them the endpoints return an honest "not configured" error.
- Deploy behind HTTPS (Render, Railway, Fly.io, a VPS with Caddy/nginx). Note: user accounts are stored in `backend/data/users.json`; move to a real database before scaling.

## 2. Run the Android app
1. Open `android/` in **Android Studio** (Koala or newer, JDK 17). Let Gradle sync.
2. Debug builds talk to `http://10.0.2.2:8787` (the emulator's route to your PC). On a physical phone, change `BASE_URL` in `app/build.gradle.kts` to your PC's LAN IP or deployed URL.
3. Press Run.

## 3. Build a release APK
1. Set the release `BASE_URL` in `app/build.gradle.kts` to your deployed **https** backend.
2. Create a keystore: `keytool -genkey -v -keystore axo.jks -keyalg RSA -keysize 2048 -validity 10000 -alias axo`
3. Create `android/keystore.properties` (git-ignored): `storeFile=../axo.jks`, `storePassword=…`, `keyAlias=axo`, `keyPassword=…`
4. `cd android && gradle wrapper && ./gradlew assembleRelease` → `app/build/outputs/apk/release/`

## Status
Done: splash, onboarding, sign up / login / guest, streaming chat (stop, regenerate, edit, copy, like/dislike, share, delete), model selector, Room history (search, rename, archive, delete), Explore GPTs (6 built-in + create/edit/delete), settings (theme, font size, custom instructions, export, delete data/account), encrypted token storage, rate limiting, input validation.

Next stages (backend endpoints already exist for search and images): image/file attachments, voice (SpeechRecognizer/TextToSpeech), search UI with citations, image generation screen, Google sign-in, password-reset email, syntax-highlighted markdown.
