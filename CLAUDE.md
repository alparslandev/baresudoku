# Bare Sudoku

- Reklamsız, mümkün olan en küçük Android sudoku. Düz Java, kütüphane yok, Gradle yok, izin yok.
- Derleme: `./build.sh` (çıktı `build/baresudoku.apk`, boyut bayt olarak yazdırılır). Test: `./test.sh`.
- Sürüm: `bun run deploy` test, sürüm artışı, APK, etiket ve GitHub sürümü. İmza anahtarı `~/.baresudoku/` altında, yedeği Alp'te.
- APK boyutu artırılmaz (24.972 bayt). Web sürümü ve 36 dil ayrı depoda: `~/Projects/baresudoku-web`, site baresudoku.com.
- Kod yorumu yok. İç sınıf, anonim sınıf ve lambda yok; her biri APK'ya ayrı sınıf ekler.
- Her adım ayrı küçük commit, mesaj tek satır Türkçe, amend yok, her commit sonrası `git push`.
- Em-dash/en-dash kullanma. Türkçe konuş, kısa yaz.
