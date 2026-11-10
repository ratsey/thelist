# The List

A shopping list that remembers what you paid. Three tabs, one gesture, and prices you type in as
you shop, kept per item so next week's list knows what milk should cost.

- **Shelf** — everything off the list but kept, one swipe away.
- **To buy** — everything you still need.
- **Basket** — everything you have already picked up.

Prices are no screen of their own: hold an item to see every price you have paid for it, as a list
or a chart.

Swipe a row right to move it forward through the shop — shelf, to buy, basket — and left to move it
back a step. Tapping a row does nothing, deliberately — a thumb brushing the list while you shop should never move or
bury anything. Hold a row to see what you have paid for it; tap its ⋮ for rename, put away and
delete. The Shelf tab fills a list from what you kept, and ⋮ → **Finish shop** empties the basket
back onto the shelf once the shopping is done.

Native Android: Kotlin, Jetpack Compose, Material 3, Room. No account, no Play Services, no cloud
you did not choose.

## Prices

There is no scanner and no camera. A photo of a receipt was never quite accurate enough to be worth
the 45 MB of bundled recognition model, so prices are typed in instead — and the app only asks where
the question is welcome. That choice lives in ⋮ → Settings:

- **When adding** (default) — swiping something into the basket asks what it cost. Skip is one tap,
  and a row that already has a price is never asked twice.
- **At checkout** — nothing interrupts the shop. ⋮ → **Finish shop** opens a sheet with a price
  field per line, so the basket is priced in one pass at the end.
- **Never** — prices only when you ask for one: hold a basket row and type it there.

A typed price goes three places at once: onto the row (the list shows what you planned to spend),
onto the item (it survives the trip to the shelf and into next week's list), and into the price
history as one priced line per checkout. Those lines are the same shape the scanner used to write,
so last/best, the chart, the brand column and the sync payload all kept working unchanged.

**Finish shop** is also how the list cleans itself. Every line in the basket goes back onto the
shelf, ready to be swiped on again next week, and the snackbar undoes the whole thing if you pressed
too early. Hold any line first — in the basket, or in the sheet — to fix its shop, aisle, amount or
price before it goes.

## Where things are

Items can carry a shop and an aisle: coffee, Walmart, `A6`. Add shops under ⋮ → **Stores**, then
hold an item (or tap its ⋮) and choose **Where and how much** to pick the shop and type the aisle,
how much to buy ("2 L", "5 lb", "a quarter") and what that costs per unit — so "1 onion, $1.25,
per lb" keeps the quantity and the rate apart instead of collapsing them into one number. An item
is still one row: the name on the left, then shop, aisle, amount and at most two prices on the same line,
so none of it costs vertical space. The shelf filter matches the aisle too, which makes the shelf the
place to ask "where do we get that?".

Price history still shows the brand an old scanned receipt printed, because "coffee at $1.40" and
"coffee at $3.50" are different jars. Prices typed in by hand show the item's own name.

At the door there is a **shop pill** on the header line: pick the shop you are standing in and its
rows show only the aisle, because the header already says where you are. Rows for other shops show
just that shop's name, since its aisle number is no help from here, and items with no shop keep
whatever aisle they have. *All shops* puts everything back, and the choice is remembered, so it is
still set when the app is reopened outside the shop.

The list is grouped by aisle, in aisle order, with everything that has no aisle at the end, so the
order you typed becomes the order you walk. A list where nobody has said where anything lives has
no headers at all. Each row can also carry an **amount** and a **price**: long-press it, choose
*Where and how much*, and type `2 L`, `5 lb` or `¼` beside what you expect to pay. Both come on the
row before the receipt history, because the amount and the price you planned are what you need with
a trolley in front of you.

A shop is a row in its own table, synced like an item, so one person adding "Walmart" gives it to
everyone sharing the list. Removing one clears the reference from its items but keeps their aisle
note, because an aisle number is still worth knowing.

## Shelf

Right moves an item forward through a shop, left moves it back a step: shelf → to buy → basket
→ shelf.

| where | swipe right | swipe left |
|---|---|---|
| Shelf | back on the list | — |
| To buy | into the basket | put away on the shelf |
| Basket | put away on the shelf | back on the list |

Putting away is not deleting: the item leaves the list but keeps its row, its spelling and its
price history, and waits on **shelf** for next time. Clearing the basket moves its rows there too,
so the shelf fills itself from ordinary shopping — next week's list is mostly last week's.

The shelf has its own tab, between Basket and Prices. Swipe a name right to put it straight back on
the list. Nothing on the shelf deletes from a swipe either: hold the row, or tap its ⋮, and delete
it from the menu. That is the trade the gestures make: no swipe and no tap destroys anything, so a mis-swipe
costs one tap to undo, and a delete is always deliberate — hold a row or tap its ⋮, then
Delete. A typo is still one menu away from gone.

Underneath it is still one `items` row: `onList = false` means the shelf, `onList` with
`completedAt` set means the basket, `onList` with no `completedAt` means to buy. One flag and one
timestamp instead of three tables, so every move is a single row update that syncs like any other
edit. Adding a name already on the shelf revives that row rather than creating a second "milk".

Because `onList` arrived after the first schema, it is an added column with a default of `1`:
the v2 → v3 migration is one `ALTER TABLE`, and a sync payload that predates the flag is read as
"on the list", which is the only thing an older writer could have meant.

## Price history

Receipt lines carry a `productKey`: the normalised name of the matched list item, or the
receipt text when nothing matched. Price history is **derived** from those lines at query time
rather than cached in its own table, so two people adding receipts to the same shared list
cannot disagree about a "last price".

- From a row: hold the item to open its history — last paid, best, average, store and date.
- Detail: a hand-drawn Canvas chart (no chart library) and every purchase.

Sizes are part of the key, so 1 L and 2 L milk keep separate histories.

## Shared lists

Sharing needs a relay; The List ships with one you can run in a minute (`/server`, zero
dependencies):

```bash
cd server && node index.js        # listens on :8080
```

Then in the app: cloud icon → paste the server address → **Start a shared list** → share the
8-character code. On the other phone: same address, **Join with a code**. New items, swipes,
renames and prices appear on both within a few seconds.

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
| Large title, live count, segmented `Shelf / To buy / Basket` | Three views of one list, one tap apart, no bottom navigation stealing thumb space |
| Add bar above the keyboard, focus retained | Shopping lists get filled in bursts |
| Swipe right = forward, swipe left = back | Colour-coded reveals teach the gesture, and no swipe destroys anything |
| No tap changes anything; the ⋮ holds the menu | a thumb brushing a row mid-shop must not move it, bury it or open a sheet |
| Day-grouped basket | Answers "did we already buy this?" without timestamps |
| Nothing is priced by guessing | A price is only ever one you typed, so history cannot invent one |
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

The version is `major.minor.build`, and **the build number is the git commit count** — so every
committed change is already a new version and there is no number to remember. Only the first two
parts are set by hand:

```properties
# gradle.properties
appVersionMajor=0     # a big release
appVersionMinor=2     # ./scripts/bump-version.sh minor
```

```bash
./scripts/bump-version.sh           # 0.2.31 — what the next build will be called
./scripts/bump-version.sh minor     # 0.2.x -> 0.3.x
./scripts/bump-version.sh major     # 0.2.x -> 1.0.x
```

`versionCode` is derived — `major * 1000000 + minor * 10000 + build` — so it rises with the
version and Android always accepts the update, with nothing kept in two places.

CI checks out the full history so it computes the same version, then publishes `v<version>` when
no release carries that tag yet. Because the build number moves with the commit, that is every
push to `main`: each push becomes an update the phones offer themselves, with no manual release
step and no "forgot to bump" state. Tags are created by CI, and a hand-pushed tag still works as
long as it matches the computed version — the workflow fails loudly when they drift.

Never move a version backwards: phones compare `versionName` to decide whether an update exists,
Android compares `versionCode` to decide whether it may install.

## Layout

```
app/src/main/java/com/ratsey/thelist/
├── MainActivity.kt          edge-to-edge Compose host
├── TheListApp.kt            application + container
├── data/                    Room entities (items, receipts, lines, outbox), DAOs, repositories
├── receipt/                 product keys that price history is grouped by
├── sync/                    HTTP protocol, payloads, push/pull engine
├── update/                  GitHub releases client + download/install pipeline
└── ui/
    ├── TheListRoot.kt       scaffold, tabs, snackbars, dialogs
    ├── ListViewModel.kt     list state, undo, sync state, prices, finish-shop, updater
    ├── PriceScreens.kt      per-item price detail and Canvas chart
    ├── components/          header, tabs, swipe row, shelf screen, add bar, banner, sync dialog
    └── theme/               palette + type scale
server/                      zero-dependency Node sync relay + protocol tests
```

## Data and migration

Schema v2 adds receipts, purchase lines, tombstones and the sync outbox; v3 adds the `onList` flag
behind the shelf. Both steps are written, and the generated schemas are committed under
`app/schemas/`, so each migration is a one-line `ALTER` tested against a real definition instead
of a reconstructed one. Anything older than v2 is still reset on upgrade
(`fallbackToDestructiveMigration`), which costs nothing while the only installs are debug ones.

### Locked before the first install

These are cheap now and expensive once phones and a relay hold real data, so they are decided:

| Decision | Value | Why now |
| --- | --- | --- |
| Schema history | exported, committed per version | migrations can't be authored or verified retroactively |
| Receipt line text | `sourceText` keeps the raw line from a scanned receipt | price keys are derived from it; without the raw text they can't be recomputed |
| Receipt photos | stored as a bare file name, never a path | the row syncs to devices where a local path means nothing |
| Sync payloads | self-describing, `"v": 1`; relay reports `protocol: 1` | a shipped client makes shape changes a compatibility problem |
| Conflicts | last-write-wins on `updatedAt`, incoming wins ties | works offline; switching to server ordering later would need dual support |
| Lists | one per device, though every row already carries `listId` | adding lists later means populating a column on live data |
| Naming | the picked-up list is the **basket**, the kept list is the **shelf**; "history" only ever means price history | a screen name lives in every hint, message and screenshot, so it is hardest to change |
| Shelf | clearing the basket and swiping left both archive the row instead of deleting it | the spelling and the price history follow the row, not a copied name |
| Duplicate names | merged on add (case/space only), not a DB constraint | a unique index would turn a simultaneous add on two phones into a sync failure |
| Invite code | 8 chars, 32-symbol alphabet | changing it strands every code already shared |
| Member token | 24 random bytes, hex | same |
| Signing key, repo | permanent before the first install | installs and update checks are pinned to both |

## Next

- Hosted sync option so sharing does not require running Node.
- WebSocket push instead of 6-second polling.
- Receipt photos: view/delete an old receipt, and cap photo storage.
- Quantity and notes per item, plus drag-to-reorder.
