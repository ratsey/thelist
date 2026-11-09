# Trolley

A shopping list that gets out of the way. Two screens, one gesture:

- **To buy** — everything you still need.
- **History** — everything you have already picked up.

Swipe a row right to move it to the other list (or tap its circle). Swipe left to delete, with undo. Tap a row for rename and delete.

Native Android: Kotlin, Jetpack Compose, Material 3, Room. No account, no network calls except the update check.

## Design notes

The layout is deliberately quiet so the list reads like paper:

| Decision | Why |
| --- | --- |
| Warm off-white canvas, white cards with a hairline border | Reads as paper; lets one green accent carry all meaning |
| Large title, live count subtitle, segmented `To buy / History` switch | Two screens, one tap apart, with no bottom navigation stealing thumb space |
| Add bar pinned to the bottom, above the keyboard | Shopping lists are filled in bursts; focus and keyboard survive each add |
| Swipe right = advance, swipe left = delete | Colour-coded reveals teach the gesture, and undo covers mistakes |
| Day-grouped history (`Today`, `Yesterday`, date) | Answers "did we already buy this?" without timestamps |
| One-time swipe hint that retires after the first move | Teaches the core interaction without a tutorial screen |
| Update card appears inline, never as a modal | An update should never interrupt a shopping trip |

Dark mode uses the same palette inverted. Dynamic colour is intentionally not used — the app should look the same on every phone.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # needs signing config, see below
```

The debug build installs as `app.trolley.lists.debug`, so it can live next to a release build.

## Getting it onto GitHub

```bash
git remote add origin git@github.com:<you>/trolley.git
git push -u origin main
```

Then point the app at that repo — this is how it finds updates:

```properties
# gradle.properties
githubOwner=your-github-username
githubRepo=trolley
```

While `githubOwner` is still the placeholder, update checks are skipped silently.

## Self-update

On launch (at most every six hours) and whenever you pick **Check for updates**, the app asks
`https://api.github.com/repos/<owner>/<repo>/releases/latest` for the newest release and compares
its tag with the installed version. When a newer tag exists, the header shows an update card:

1. **Update** downloads the release APK with the system `DownloadManager` (progress in the card and in the notification shade; it keeps going if the app is backgrounded).
2. The APK lands in the app's external files directory and is shared with the package installer through a `FileProvider`.
3. Android shows its own install prompt. The first time, you also grant "install unknown apps" for Trolley — the card links straight to that setting.

Two constraints are inherent to sideloaded self-updates:

- **Every release must be signed with the same key.** Android will not install an update signed by a different key. Generate the key once, keep it, and use it for every release.
- **The repository and its releases must be public**, because the check and the download are unauthenticated.

If a download finishes while the app is closed, it is picked up on the next launch and the card offers **Install**.

## Release signing (one-time setup)

```bash
./scripts/generate-keystore.sh
```

This creates `release.jks` and a git-ignored `keystore.properties` for local release builds, then prints the values to paste into **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of `release.jks` (printed by the script) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (`trolley`) |
| `KEY_PASSWORD` | key password |

Back up `release.jks`. Losing it means no future release can update an installed copy.

## Shipping a version

```bash
git tag v0.2.0
git push origin v0.2.0
```

`.github/workflows/android.yml` derives `versionName` (`0.2.0`) and `versionCode` (`20000`) from the
tag, builds a signed APK, and attaches it to a GitHub release. Phones on an older version see the
update card the next time they open the app. Pushes and pull requests build a debug APK instead.

## Layout

```
app/src/main/java/app/trolley/lists/
├── MainActivity.kt          edge-to-edge Compose host
├── TrolleyApp.kt            application + container
├── data/                    Room entity, DAO, repository, DataStore settings
├── update/                  GitHub releases client + download/install pipeline
└── ui/
    ├── TrolleyRoot.kt       scaffold, tabs, snackbars, dialogs
    ├── ListViewModel.kt     list state, undo stack, update state machine
    ├── components/          header, tabs, swipe row, add bar, banner, sheet
    └── theme/               palette + type scale
```

Items store `completedAt` (null = to buy) rather than a boolean, so the two screens are two
queries over one table. `listId` and `updatedAt` are already on every row so shared lists can be
added without a schema rewrite.

## Next

- Shared lists: sync `listId` between people (needs a backend or a file-based sync target).
- Quantity and notes per item, plus reorder.
- Optional categories/aisles to sort the list.
