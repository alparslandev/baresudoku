# App Store listing

Bundle ID com.baresudoku (team 9X2XU3N96X), SKU baresudoku, primary language English (U.S.), free in all territories. Platforms on the same record: iOS (iPhone and iPad), macOS (Mac Catalyst), tvOS, visionOS; each platform is submitted separately with its own build and screenshots.

Texts (name, subtitle, keywords, URLs, promotional text, description; English and Turkish) and the screenshot lists per display type live in `listing.json`. `bun scripts/asc.js metadata <version> [ios|mac|tv|vision]` uploads them together with the category (Games: Puzzle, Board), the age rating answers (everything "None" or "No", result 4+) and the content rights declaration (no third-party content). The App Review contact (name, phone, email) is read from the `review` object in `~/.baresudoku/asc.json`, never from the repo; the review notes are `reviewNotes` in `listing.json`, next to `copyright` and the per-locale `privacyPolicyText` (Apple requires the policy text itself because the record includes tvOS). visionOS versions also get `hasHighMotionLabel: false` (a static board has no high motion). `submit` re-applies all text fields before submitting; only screenshots need `metadata`. `bun scripts/asc.js review <version> [platform]` sets only those. `bun run deploy` uploads the builds and submits them for review.

## By hand in App Store Connect (no API for these)

- App Privacy: "Data Not Collected". No tracking, no identifiers, no third-party SDKs.
- Pricing and Availability: Free, all territories.
- Export compliance: `ITSAppUsesNonExemptEncryption` is false in Info.plist, no question is asked.
- Advertising identifier: not used.
- visionOS only: the High Motion label (`hasHighMotionLabel`) is required for submission but not exposed by the API (spec 4.5); answer "No" on the visionOS version page before running `submit`.

## Screenshots (this folder)

- iPhone 6.9" (1320 x 2868): `1-board.png` to `5-dark.png`. Captions: "No ads, no account", "Lock a digit, fill cells fast", "Hints explain the logic", "Four levels, one solution each", "Dark mode".
- iPad 13" (2064 x 2752), Mac (2560 x 1600), Apple TV (1920 x 1080), Apple Vision Pro (3840 x 2160): added per platform as each build lands; the file lists are in `listing.json`.
