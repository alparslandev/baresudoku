import { readFileSync, writeFileSync, copyFileSync, mkdirSync, rmSync } from "fs";

const STORE = new URL("../android/store/", import.meta.url);
const OUT = new URL("../fastlane/metadata/android/", import.meta.url);
const SHOTS = ["1-board.png", "2-lock.png", "3-hint.png", "4-menu.png", "5-dark.png"];
const texts = JSON.parse(readFileSync(new URL("listing.json", STORE), "utf8"));
rmSync(OUT, { recursive: true, force: true });
for (const [lang, l] of Object.entries(texts)) {
  const dir = new URL(`${lang}/`, OUT);
  mkdirSync(new URL("images/phoneScreenshots/", dir), { recursive: true });
  writeFileSync(new URL("title.txt", dir), l.title + "\n");
  writeFileSync(new URL("short_description.txt", dir), l.short + "\n");
  writeFileSync(new URL("full_description.txt", dir), l.full + "\n");
  copyFileSync(new URL("icon-512.png", STORE), new URL("images/icon.png", dir));
  copyFileSync(new URL("feature.png", STORE), new URL("images/featureGraphic.png", dir));
  SHOTS.forEach((file, i) => copyFileSync(new URL(file, STORE), new URL(`images/phoneScreenshots/${i + 1}.png`, dir)));
}
console.log(`fastlane: ${Object.keys(texts).join(", ")}`);
