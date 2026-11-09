# The List

A shopping list that remembers what you paid. Three screens, one gesture, and a receipt
scanner that turns a photo into price history.

- **To buy** — everything you still need.
- **Basket** — everything you have already picked up.
- **Prices** — every price you have paid for each item, as a list or a chart.

Swipe a row right to move it to the other list (or tap its circle). Swipe left to delete, with
undo. Tap a row for rename and delete. Tap the camera to scan a receipt.

Native Android: Kotlin, Jetpack Compose, Material 3, Room, ML Kit. No account, no Play
Services, no cloud you did not choose.

## Receipt scanning

1. The camera icon launches the system camera (no CAMERA permission, no preview code).
2. The photo is OCR'd **on the device** with ML Kit's bundled Latin model, then parsed by a
   conservative heuristic parser: store name, date (day-first or month-first from your locale),
   currency, total, and each `name … price` line. Footer noise — `TOTAL`, `CARD`, `CHANGE`,
   `VAT`, `SUMME`, `TVA` — is dropped rather than guessed at.
3. A review screen shows the photo, editable store/date/total, and every line with a checkbox.
   Lines are matched by token overlap ("TESCO SEMI SKIMMED MILK 2L" → *Milk*) against the
   **basket first** — what you swiped during this shop, within 36 hours — and only then against
   what is still to buy. Swiping is your own record of picking something up, so it beats a name
   that merely looks familiar; if you shop without swiping, the list alone is enough.
4. A line that matched something on the list can move it to the basket as the receipt saves.
   That is a setting (overflow menu → Settings, on by default) with a per-receipt switch on the
   review screen, so a scan can also record prices and leave the list untouched.
5. Saving stores the receipt and its priced lines, which is what fills the Prices tab.

Anything the parser is unsure about stays empty or editable — it never invents a price.

OCR runs fully offline, which is why the APK is ~45 MB: ML Kit's recognition model ships
once per CPU architecture inside the app rather than being downloaded from Play Services.
Per-ABI split APKs (roughly 11–18 MB each) are the obvious next size win.

## Price history

Receipt lines carry a `productKey`: the normalised name of the matched list item, or the
receipt text when nothing matched. Price history is **derived** from those lines at query time
rather than cached in its own table, so two people adding receipts to the same shared list
cannot disagree about a "last price".

- List: last paid, best price, store, date, number of purchases.
- Detail: latest price card, a hand-drawn Canvas chart (no chart library), and every purchase.

Sizes are part of the key, so 1 L and 2 L milk keep separate histories.

## Shared lists

Sharing needs a relay; The List ships with one you can run in a minute (`/server`, zero
dependencies):

```bash
cd server && node index.js        # listens on :8080
```

Then in the app: cloud icon → paste the server address → **Start a shared list** → share the
8-character code. On the other phone: same address, **Join with a code**. New items, swipes,
renames and scanned receipts appear on both within a few seconds.

How it works:

- Every local write is also appended to a `sync_outbox` with the full entity as JSON.
- The app pushes the outbox and pulls everything newer than its last sequence every ~6 seconds
  while open, immediately after edits, and on demand.
- Conflicts resolve last-write-wins on `updatedAt`; deletes travel as tombstones (`deletedAt`)
  so a removed item does not come back on the next sync.
- Offline is a normal state: the list keeps working and the header cloud icon turns red until
  it catches up.
- Joining replaces the local list with the shared one (the dialog says so) — you are adopting
  someone else's shopping history, not merging two.

Security and hosting notes live in [server/README.md](server/README.md). In short: the code is
the invite secret, use HTTPS, and the server can see item names and prices.

The intended production home is a single small VPS, so `deploy/kamatera/` holds the deployment
as a stub: a Docker Compose stack (Caddy for automatic HTTPS, the relay, a volume for the
database), an idempotent `setup-vps.sh` for a fresh Ubuntu box, and `deploy.sh` to ship it. It
uses placeholders and nothing is deployed yet — [deploy/kamatera/README.md](deploy/kamatera/README.md)
has the runbook. Once a domain points at the server, set `syncServerUrl` in `gradle.properties`
and the share dialog pre-fills the address.

If you would rather not run a server, the sync layer is deliberately a small interface
(`SyncClient` + `SyncEngine`): swapping in a hosted backend is a contained change.

## Design notes

The layout stays quiet so the list reads like paper:

| Decision | Why |
| --- | --- |
| Warm off-white canvas, white cards, hairline borders | Paper-like; one green accent carries all meaning |
| Large title, live count, segmented `To buy / Basket / Prices` | Three views of one list, one tap apart, no bottom navigation stealing thumb space |
| Add bar above the keyboard, focus retained | Shopping lists get filled in bursts |
| Swipe right = advance, swipe left = delete | Colour-coded reveals teach the gesture; undo covers mistakes |
| Day-grouped basket | Answers "did we already buy this?" without timestamps |
| Receipt review before saving | OCR is good, not perfect: confirm, don't trust blindly |
| Update card inline, never a modal | An update should never interrupt a shopping trip |

Dark mode inverts the same palette. Dynamic colour is intentionally not used.

## Build

JDK 17 and Android SDK 35.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease        # needs signing config, see below
```

Debug builds install as `com.ratsey.thelist.debug` alongside a release build.

## Getting it onto GitHub

```bash
git remote add origin git@github.com:<you>/thelist.git
git push -u origin main
```

Point the app at that repo so it can find updates:

```properties
# gradle.properties
githubOwner=your-github-username
githubRepo=thelist
```

While `githubOwner` is the placeholder, update checks are skipped silently.

## Self-update

On launch (at most every six hours) and from **Check for updates**, the app asks
`api.github.com/repos/<owner>/<repo>/releases/latest` for the newest release and compares tags.
When a newer tag exists, the header shows an update card:

1. **Update** downloads the release APK with the system `DownloadManager` (progress in the card
   and the notification shade; it continues if the app is backgrounded).
2. The APK is shared with the package installer through a `FileProvider`.
3. Android shows its install prompt. The first time, you grant "install unknown apps" — the card
   links straight to that setting.

Two inherent constraints of sideloaded self-updates:

- **Every release must be signed with the same key**, or Android refuses the update.
- **The repo and its releases must be public**, because the check and download are unauthenticated.

A download that finishes while the app is closed is offered again on next launch.

## Release signing

```bash
./scripts/generate-keystore.sh
```

Creates `release.jks` and a git-ignored `keystore.properties` for local release builds, then
prints the values for **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of `release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (`thelist`) |
| `KEY_PASSWORD` | key password |

Back up `release.jks`: lose it and no future release can update an installed copy.

## Shipping a version

The version lives in exactly one place, `gradle.properties`:

```bash
./scripts/bump-version.sh patch     # 0.2.0 -> 0.2.1
git commit -am "Release 0.2.1"
git push
```

`versionCode` is derived from `versionName` (`major*10000 + minor*100 + patch`), so there is no
second number to keep in sync. On every push to `main`, CI compares the committed version with
the existing releases: if `v<version>` is new it runs the unit tests, builds a signed APK,
creates the tag and publishes the release; if the release already exists it does nothing. That
makes releasing a one-command operation and means the tag can never disagree with the build.

Tags are created by CI, but pushing one by hand still works as long as it matches
`gradle.properties` — the workflow fails loudly if they drift. Never move a version backwards:
phones compare `versionName` to decide whether an update exists, Android compares `versionCode`
to decide whether it may install.

## Layout

```
app/src/main/java/com/ratsey/thelist/
├── MainActivity.kt          edge-to-edge Compose host
├── TheListApp.kt            application + container
├── data/                    Room entities (items, receipts, lines, outbox), DAOs, repositories
├── receipt/                 parser, item matcher, ML Kit OCR, camera photo helpers
├── sync/                    HTTP protocol, payloads, push/pull engine
├── update/                  GitHub releases client + download/install pipeline
└── ui/
    ├── TheListRoot.kt       scaffold, tabs, snackbars, dialogs
    ├── ListViewModel.kt     list state, undo, scan state, sync state, prices, updater
    ├── ScanReviewScreen.kt  receipt confirmation and editing
    ├── PriceScreens.kt      price list, product detail, Canvas chart
    ├── components/          header, tabs, swipe row, add bar, banner, sheet, sync dialog
    └── theme/               palette + type scale
server/                      zero-dependency Node sync relay + protocol tests
```

## Data and migration

Schema v2 adds receipts, purchase lines, tombstones and the sync outbox. The generated schema is
committed under `app/schemas/`, so a future migration to v3 can be written and tested against a
real v2 definition instead of a reconstructed one. Until that migration exists, local data is
still reset on upgrade (`fallbackToDestructiveMigration`) — the first task before a wider
release.

### Locked before the first install

These are cheap now and expensive once phones and a relay hold real data, so they are decided:

| Decision | Value | Why now |
| --- | --- | --- |
| Schema history | exported, committed per version | migrations can't be authored or verified retroactively |
| Receipt line text | `sourceText` keeps the raw OCR line | price keys are derived; without the raw text they can't be recomputed |
| Receipt photos | stored as a bare file name, never a path | the row syncs to devices where a local path means nothing |
| Sync payloads | self-describing, `"v": 1`; relay reports `protocol: 1` | a shipped client makes shape changes a compatibility problem |
| Conflicts | last-write-wins on `updatedAt`, incoming wins ties | works offline; switching to server ordering later would need dual support |
| Lists | one per device, though every row already carries `listId` | adding lists later means populating a column on live data |
| Naming | the picked-up list is the **basket**; "history" only ever means price history | a screen name lives in every hint, message and screenshot, so it is hardest to change |
| Duplicate names | merged on add (case/space only), not a DB constraint | a unique index would turn a simultaneous add on two phones into a sync failure |
| Invite code | 8 chars, 32-symbol alphabet | changing it strands every code already shared |
| Member token | 24 random bytes, hex | same |
| Signing key, repo | permanent before the first install | installs and update checks are pinned to both |

## Next

- Hosted sync option so sharing does not require running Node.
- WebSocket push instead of 6-second polling.
- Receipt photos: view/delete an old receipt, and cap photo storage.
- Quantity and notes per item, plus drag-to-reorder.
