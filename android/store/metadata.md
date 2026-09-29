# Google Play listing

Package com.baresudoku. Upload key `~/.baresudoku/release.jks` (alias baresudoku, valid until 2054), Play App Signing on. Bundle: `bun run aab` writes `android/build/bundle/baresudoku.aab`; the first upload is done by hand in Play Console (Google does not allow it through the API), later ones by `bun run deploy`. Free, all countries. Default language English (United States).

Texts (title, short description, full description; en-US and tr-TR) live in `listing.json`; `bun scripts/play.js listing` uploads them with the icon, feature graphic and screenshots below. Release notes for both stores come from `release-notes.json` in the repository root.

## Graphics (this folder)

- App icon: `icon-512.png` (512 x 512, no rounded corners, Play adds its own).
- Feature graphic: `feature.png` (1024 x 500), source `feature.svg`.
- Phone screenshots: `1-board.png` to `5-dark.png` (1080 x 1920). Captions: "No ads, no account", "Lock a digit, fill cells fast", "Hints explain the logic", "Four levels, one solution each", "Dark mode".

## Store settings (by hand)

- App or game: Game. Category: Puzzle. Tags: Sudoku, Puzzle, Brain games, Offline.
- Contact email: shown publicly, Alp decides (suggestion: alp.develioglu@gmail.com). Website: https://baresudoku.com/
- Privacy policy: https://baresudoku.com/privacy/

## App content declarations (by hand, no API)

- Privacy policy: https://baresudoku.com/privacy/
- Ads: No, the app does not contain ads.
- App access: All functionality is available without special access (no login).
- Content rating (IARC): category Game. Every question No: no violence, no fear, no sexuality, no nudity, no profanity, no drugs, alcohol or tobacco, no gambling or simulated gambling, no user-generated content, no user interaction or sharing, no location sharing, no purchases, no web browser. Expected rating: Everyone / PEGI 3.
- Target audience and content: age groups 13-15, 16-17, 18 and over. Not designed for children; the store listing does not unintentionally appeal to children.
- News app: No. COVID-19 contact tracing or status app: No.
- Data safety: does not collect or share any user data. Nothing is sent, nothing is stored off the device, so no encryption in transit and no deletion request mechanism apply.
- Advertising ID: not used.
- Government app: No. Financial features: none. Health: no health features.
- Photo and video permissions, foreground services, exact alarms, VPN, accessibility API: none used.
