# Bare Sudoku

- Reklamsız, mümkün olan en küçük Android sudoku. Düz Java, kütüphane yok, Gradle yok, izin yok.
- Depo iki platform: `android/` (düz Java) ve `ios/` (C motor + Objective-C arayüz). Android derleme: `android/build.sh` (çıktı `android/build/baresudoku.apk`, boyut bayt olarak yazdırılır), test: `android/test.sh`.
- Android sürüm: `bun run deploy` test, sürüm artışı, APK, etiket ve GitHub sürümü. İmza anahtarı `~/.baresudoku/` altında, yedeği Alp'te.
- iOS: `ios/test.sh` (C testleri), `ios/build.sh sim` (simülatör), `bun run ipa` (imzalı IPA), `bun run deploy:ios` (build numarası + arşiv + App Store Connect yüklemesi + etiket). Xcode projesi `ios/BareSudoku.xcodeproj`, otomatik imza takım 9X2XU3N96X, bundle com.baresudoku. Takımda kayıtlı cihaz olmadığı için arşiv CODE_SIGNING_ALLOWED=NO ile alınır, imza export'ta atılır. İkon tek kaynak `ios/icon.svg` (favicon), katalog derlemede üretilir; başka ikon boyutu eklenmez.
- Üç platformda aynı kurallar (giriş, baştan başla, notları doldur/temizle); web deposundaki engine.js ile birlikte değiştir.
- APK boyutu artırılmaz (24.972 bayt). Web sürümü ve 36 dil ayrı depoda: `~/Projects/baresudoku-web`, site baresudoku.com.
- Giriş kuralı iki platformda aynı (Game.key/Game.tap): tuş seçili boş hücreye yazar; yazacak yer yoksa ya da aynı tuşa ikinci basışta rakam kilitlenir ve dokunulan hücrelere yazılır, tekrar basınca çözülür. Web deposundaki engine.js ile birlikte değiştir.
- Kod yorumu yok. İç sınıf, anonim sınıf ve lambda yok; her biri APK'ya ayrı sınıf ekler.
- Her adım ayrı küçük commit, mesaj tek satır Türkçe, amend yok, her commit sonrası `git push`.
- Em-dash/en-dash kullanma. Türkçe konuş, kısa yaz.
