# Working on The List

A dense, dark Android shopping list (`com.ratsey.thelist`): three tabs — Shelf, To buy, Basket — a
price you type in as you shop, and a home-grown self-update that ships APKs from GitHub releases.
Kotlin + Jetpack Compose (Material 3), Room, no Hilt, no DI framework, no OCR, no network library.

## Build and test

JDK 17 and Android SDK 35. The Android SDK must be found through `ANDROID_HOME` or a
`local.properties` file with `sdk.dir=` — it is gitignored, so a fresh clone has neither and the
build fails until one is set. That is the single most common first-run problem.

```bash
./gradlew assembleDebug testDebugUnitTest     # what CI runs; must stay green
./gradlew testDebugUnitTest                   # unit tests only, ~10s warm
node --test 'server/test/*.mjs'                      # sync relay protocol tests
```

Test count after a run (the suite is the spec; read it before changing pricing maths):

```bash
grep -h -o 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml \
  | sed 's/[^0-9]//g' | awk '{s+=$1} END {print s}'
```

There is no emulator and no device in CI or in a cloud agent: nothing visual, gestural or
keyboard-related can be verified. Say so instead of claiming a screen "works".

## Layout

- `app/src/main/java/com/ratsey/thelist/data/` — Room entities/DAOs, `Pricing.kt` (units, bases,
  conversions), `PriceMatch.kt` (offer comparison), repositories, `SettingsStore`.
- `app/src/main/java/com/ratsey/thelist/ui/` — `ListViewModel` (the whole state machine),
  `TheListRoot`, screens and `components/` (sheets, rows, dialogs).
- `app/src/main/java/com/ratsey/thelist/sync/` — sync client/engine for the relay in `server/`.
- `app/src/main/java/com/ratsey/thelist/update/` — update checker and APK download.
- `server/` — the tiny Node sync relay; `deploy/kamatera/` — its VPS deployment.
- `scripts/bump-version.sh`, `scripts/generate-keystore.sh`.

## Invariants worth not breaking

- **Version numbers are derived, not remembered.** `versionName` is `major.minor.commitCount`
  (`git rev-list --count HEAD`), `versionCode` is `major*1000000 + minor*10000 + build`. Any commit
  is already a new build; only `./scripts/bump-version.sh minor|major` changes the first two parts.
  Never lower a shipped number — phones compare `versionName`, Android compares `versionCode`.
- **One implementation of unit-rate maths**: `unitPriceOf`. If a screen needs a price per unit, call
  it; do not divide price by quantity anywhere else.
- **Comparing two prices happens by restating one into the other's `PriceBasis`.** If the measures
  cannot meet (a weight against a length), return null and show "Not comparable". Never guess.
- **A bare number in the "per" field is a count** (`"60"` means sixty of the thing). A single
  countable unit always prints as `each`, whichever word was typed. Bought-in-multiples keep their
  noun (`30 pods`).
- **Both unit systems are always offered**; the metric/imperial setting only orders the units
  inside a group (`unitGroups`), because a US shopper buys by the pound in one aisle and the kilo
  in the next.
- **The shelf prints the rate and nothing else** (`itemMeta(rateOnly = true)`); the To buy list
  prints the pack price with the rate beside it. Money tokens are the only difference.

## UI traps already paid for

- A vertical scroller **inside a `ModalBottomSheet`** fights the sheet's drag-to-dismiss: overscroll
  closes the dialog under the user's finger. Use a horizontal row, or fix the height.
- `Scaffold.bottomBar` subcomposes *all* its children — never emit two siblings there.
- `FlowRow` needs `@OptIn(ExperimentalLayoutApi::class)` on this Compose version.
- The price card measures itself with `BoxWithConstraints`; under 300dp the figure drops a
  typography size and the unit price moves to its own line, for narrow screens and foldable covers.
- Colour comes from theme roles, never literals from a design mockup.

## Working rules

- **Fetch before you start, every time.** This repository is committed to from more than one
  place — other machines, other agents — so a clone is out of date the moment it is made, and the
  history here has already been rewritten once. Begin with `git fetch`, read
  `git log --oneline HEAD..origin/main`, and rebase your work onto it. If a push is rejected as
  non-fast-forward, fetch and rebase; never force.
- Comments explain *why*. Do not restate the code.
- Keep changes to what was asked; no drive-by refactors.
- **Never commit** keystores, `keystore.properties`, `.env`, `deploy/kamatera/deploy.env`, or
  anything with a token. `artifacts/` and `build/` are gitignored.
- `main` triggers `.github/workflows/android.yml`: debug build + tests, sync server tests, then a
  signed release published to GitHub Releases — which installed apps then auto-install. Treat a push
  to `main` as a release, not a save.
- Releases need four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD`. Without them the release job fails by design (in-app updates only work when every
  release is signed with the same key).
- Local builds are debug (`com.ratsey.thelist.debug`) and install beside a release build.
