# Bare Sudoku

Ad-free, tiny Sudoku for Android. Plain Java, zero libraries, no permissions, no tracking.

Download the APK from the [website](https://baresudoku.com) or from [GitHub Releases](https://github.com/alparslandev/baresudoku/releases).

<img src="screenshot.png" width="270" alt="Bare Sudoku on a phone">

## Features

- Four levels (Easy, Medium, Hard, Expert). Every puzzle is solvable by pure logic, no guessing.
- Notes, undo, erase, fill all notes, and hints that explain the reasoning: singles, locked candidates, pairs and triples, X-Wing, Y-Wing, Swordfish, XYZ-Wing.
- Row, column, box and same-digit highlights, mistake marking (can be turned off), automatic save, dark mode, landscape and tablet layouts.
- English and Turkish, following the system language.
- APK about 25 KB. Android 8 and newer. No internet permission.

## Build

Needs Android SDK build-tools 36.0.0, platform 36 and JDK 17. No Gradle.

```sh
./build.sh   # build/baresudoku.apk, prints the size in bytes
./test.sh    # generator, solver and game state tests on the desktop JVM
```

## Release

Run `bun install` once, then `bun run deploy`. It runs the tests, bumps the version, builds the APK, tags the commit, creates a GitHub release with the APK and publishes the site to Cloudflare. The signing key lives in `~/.baresudoku`; back it up, updates are only possible with the same key.

## License

MIT
