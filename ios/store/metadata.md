# App Store listing

Bundle ID com.baresudoku (team 9X2XU3N96X), SKU baresudoku, primary language English (U.S.), free in all territories. Platforms on the same record: iOS (iPhone and iPad), macOS (Mac Catalyst), tvOS, visionOS; each platform is submitted separately with its own build and screenshots.

Texts (name, subtitle, keywords, URLs, promotional text, description; English and Turkish) and the screenshot lists per display type live in `listing.json`. `bun scripts/asc.js metadata <version> [ios|mac|tv|vision]` uploads them together with the category (Games: Puzzle, Board), the age rating answers (everything "None" or "No", result 4+) and the content rights declaration (no third-party content). `bun run deploy` uploads the builds and submits them for review.

## By hand in App Store Connect (no API for these)

- App Privacy: "Data Not Collected". No tracking, no identifiers, no third-party SDKs.
- Pricing and Availability: Free, all territories.
- Export compliance: `ITSAppUsesNonExemptEncryption` is false in Info.plist, no question is asked.
- Advertising identifier: not used.
- Review notes: No account or setup. Tap a level in the first menu to start. Tap a digit key twice to lock it and place it in several cells.

## Screenshots (this folder)

- iPhone 6.9" (1320 x 2868): `1-board.png` to `5-dark.png`. Captions: "No ads, no account", "Lock a digit, fill cells fast", "Hints explain the logic", "Four levels, one solution each", "Dark mode".
- iPad 13" (2064 x 2752), Mac (2560 x 1600), Apple TV (1920 x 1080), Apple Vision Pro (3840 x 2160): added per platform as each build lands; the file lists are in `listing.json`.
