# iOS için tek seferlik ücret: analiz (2026-09-29)

Durum: karar yok, uygulama daha çıkmadı. Çıkış öncesi bu dosyaya bak, analizi yeniden yapma; yalnızca /stats/ verisiyle rakamları güncelle.

Soru: iOS (ve aynı kayıttaki macOS, tvOS, visionOS) tek seferlik X dolara satılsın mı? Uygulama içi satın alma, abonelik, reklam yok. Web ve Android ücretsiz kalır.

## Mekanik
- Paid Apps Agreement (banka, W-8BEN) imzalanmadan ücretli yapılamaz.
- Komisyon %30; Small Business Program'a kaydolunca %15 (yıllık < 1M$, kayıt ayrı).
- Universal purchase: tek ödeme iOS + macOS + tvOS + visionOS. Family Sharing açılabilir.
- Apple çoğu vitrinde satıcı, KDV'yi keser; sana net USD gelir, gelir beyanı Türkiye'de. Vergi için muhasebeciye sor.

## Lehte
- Ücretli uygulamanın zaafı "denemeden alamama": web sürümü ücretsiz deneme. "Web'de oyna, beğendiysen uygulamayı al" dürüst ve nadir konum.
- Marka (reklam, hesap, izleme yok) ile "abonelik ve satın alma yok, bir kez öde" uyumlu; bu kitle bu modele para verir.
- tvOS ve visionOS'ta web çalışmıyor, rekabet az; Apple TV'de reklamsız sudoku gerçekten farklı.
- Ücretli kullanıcı az ama gürültüsüz: bot indirme, sahte yorum, destek yükü düşük.

## Aleyhte ve riskler
- Keşfedilebilirlik: "sudoku" aramasında milyon yorumlu ücretsiz devler var; ücretli indie organik olarak görünmez, indirme ücretsize göre 10-50 kat düşer (sektör deneyimi).
- Tek trafik kaynağı baresudoku.com: satış = web trafiği × tıklama × satın alma.
- Ücretli kullanıcı beklentisi: "4 cihazda oynuyorum, ilerleme neden senkron olmuyor?" ilk şikayet olur. iCloud key-value senkronu (kendi sunucu yok) gizlilik vaadini bozmadan yapılabilir; yol haritasında dursun.
- Erişilebilirlik (VoiceOver, Dynamic Type) ücretli uygulamada yorumlara yansır; elle çizilen arayüzde durumu kontrol et.
- MIT lisans: kodun ücretsiz klonu App Store'a konabilir. Guideline 4.1 ve isim/ikon kısmen korur. Hobi ölçeğinde küçük risk.
- Ücretli uygulamanın yorumu az olur, ilk 5-10 yorum belirleyici; ilk sürüm kusursuz olmalı.

## Gelir beklentisi (varsayımlar açık)
Örnek: günde 1.000 dokunmatik açılış, yarısı iPhone/iPad (500); App Store bağlantısına tıklama %0,5 (2-3 kişi); ücretli sayfada satın alma %10-20 (siteden gelen ikna olmuş). Günde 0-1 satış. 2,99$ × %85 ≈ 2,5$ net → ayda 40-80$. 99$ geliştirici ücreti 40 satışta çıkar. Gerçekçi hedef: Apple masrafını karşılamak. Web trafiği 10 katsa rakamlar 10 kat. Gerçek açılış sayısı: baresudoku.com/stats/ (Touch screen satırı).

## Fiyat
- Talep 0,99-2,99$ arasında yatay: karar "para verir miyim". 0,99$ ucuz sinyali, 4,99$+ marka ister.
- Dört platform + bir kez öde ile 2,99$ (ya da 3,99$).
- Türkiye vitrini için ayrı, düşük yerel fiyat gir (Apple ülke bazlı fiyat izin veriyor).

## Ücretliye geçilirse değişecek metinler
- Site SSS "Bare Sudoku ücretsiz mi?" (223 dil): "App Store sürümü X$, bir kez ödenir, reklam ve satın alma yok" eklenir. "iPhone/iPad sürümü var mı?" cevabına App Store bağlantısı; baresudoku-web `build.py` içinde `APP_STORE_URL` hazır, boş.
- Mağaza alt başlığı: "Pay once, no ads, no account, offline".
- Siteden App Store'a bağlantıya Apple kampanya parametresi (`?ct=website`): App Store Connect kaynağa göre indirme gösterir, biz izlemeyiz.
- Mağaza metninde açıkça yaz: App Store sürümü 99$ geliştirici ücretini ve komisyonu karşılar, web ücretsiz kalır.

## Öneri
- Amaç para ise ücretli iOS bunu yapmaz, en fazla masrafı çıkarır. Amaç reklamsız ve aboneliksiz sürdürülebilir dürüst ürün ise doğru model.
- 2,99$, universal purchase, Family Sharing açık, Türkiye için yerel fiyat.
- Ücretsiz web + ücretsiz Android + ücretli iOS bileşimi açık kaynakta yaygın.
- Ücretli başlayıp gerekirse indirim yapmak, ücretsizden ücretliye geçmekten güvenli.
- Karar öncesi /stats/ en az bir hafta veri toplasın; dokunmatik açılış sayısına göre yukarıdaki hesabı güncelle.
