import { readFileSync } from "fs";
import { createSign } from "crypto";

const PKG = "com.baresudoku";
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PKG}`;
const UPLOAD = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PKG}`;
const STORE = new URL("../android/store/", import.meta.url);
const MANIFEST = new URL("../android/AndroidManifest.xml", import.meta.url);
const PLAY_LANG = { en: "en-US", tr: "tr-TR" };
const b64 = s => Buffer.from(s).toString("base64url");

async function token() {
  const sa = JSON.parse(readFileSync(`${process.env.HOME}/.baresudoku/play.json`, "utf8"));
  const now = Math.floor(Date.now() / 1000);
  const header = b64(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/androidpublisher", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 }));
  const signer = createSign("RSA-SHA256");
  signer.update(`${header}.${claims}`);
  const jwt = `${header}.${claims}.${signer.sign(sa.private_key, "base64url")}`;
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!r.ok) throw new Error(`token ${r.status}: ${await r.text()}`);
  return (await r.json()).access_token;
}

async function call(tok, method, url, body, type = "application/json") {
  const r = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${tok}`, "Content-Type": type },
    body: body === undefined ? undefined : type === "application/json" ? JSON.stringify(body) : body,
  });
  const text = await r.text();
  if (!r.ok) throw new Error(`${method} ${url} ${r.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

async function withEdit(fn) {
  const tok = await token();
  const edit = (await call(tok, "POST", `${API}/edits`, {})).id;
  await fn(tok, edit);
  await call(tok, "POST", `${API}/edits/${edit}:commit`);
}

function manifestVersionCode() {
  return readFileSync(MANIFEST, "utf8").match(/android:versionCode="(\d+)"/)[1];
}

async function upload(tok, edit, aab) {
  try {
    return String((await call(tok, "POST", `${UPLOAD}/edits/${edit}/bundles?uploadType=media`, readFileSync(aab), "application/octet-stream")).versionCode);
  } catch (e) {
    if (!/already been used/.test(String(e.message))) throw e;
    const code = manifestVersionCode();
    const existing = (await call(tok, "GET", `${API}/edits/${edit}/bundles`)).bundles || [];
    if (!existing.some(b => String(b.versionCode) === code)) throw e;
    console.log(`Play: versionCode ${code} zaten yuklu, mevcut paket kullaniliyor`);
    return code;
  }
}

async function publish(aab, version, track, status) {
  const notes = JSON.parse(readFileSync(new URL("../release-notes.json", import.meta.url), "utf8"));
  await withEdit(async (tok, edit) => {
    const versionCode = await upload(tok, edit, aab);
    await call(tok, "PUT", `${API}/edits/${edit}/tracks/${track}`, {
      track,
      releases: [{
        name: version,
        versionCodes: [versionCode],
        status,
        releaseNotes: Object.entries(notes).map(([lang, text]) => ({ language: PLAY_LANG[lang], text })),
      }],
    });
    console.log(`Play: ${version} (versionCode ${versionCode}) ${track} kanalinda, durum ${status}`);
  });
}

const RETRYABLE = /draft app|precondition/i;

async function release(aab, version, track) {
  const attempts = [[track, "completed"], [track, "draft"]];
  for (const fallback of ["alpha", "internal"]) if (fallback !== track) attempts.push([fallback, "draft"]);
  let last;
  for (const [t, status] of attempts) {
    try {
      await publish(aab, version, t, status);
      if (t !== track) console.log(`Play: ${track} kanali henuz acik degil, ${t} kanali kullanildi`);
      if (status === "draft") console.log(`Play: uygulama henuz yayinlanmamis, ${version} ${t} kanalina taslak olarak yuklendi; Play Console'da Surumu incele ve Yayinla ile gonder`);
      return;
    } catch (e) {
      last = e;
      if (!RETRYABLE.test(String(e.message))) throw e;
      console.log(`Play: ${t}/${status} reddedildi: ${String(e.message).replace(/\s+/g, " ").slice(0, 160)}`);
    }
  }
  throw last;
}

async function listing() {
  const texts = JSON.parse(readFileSync(new URL("listing.json", STORE), "utf8"));
  const images = [
    ["icon", ["icon-512.png"]],
    ["featureGraphic", ["feature.png"]],
    ["phoneScreenshots", ["1-board.png", "2-lock.png", "3-hint.png", "4-menu.png", "5-dark.png"]],
  ];
  await withEdit(async (tok, edit) => {
    for (const [lang, l] of Object.entries(texts)) {
      await call(tok, "PUT", `${API}/edits/${edit}/listings/${lang}`, { language: lang, title: l.title, shortDescription: l.short, fullDescription: l.full });
      for (const [type, files] of images) {
        await call(tok, "DELETE", `${API}/edits/${edit}/listings/${lang}/${type}`);
        for (const file of files) {
          await call(tok, "POST", `${UPLOAD}/edits/${edit}/listings/${lang}/${type}?uploadType=media`, readFileSync(new URL(file, STORE)), "image/png");
        }
      }
    }
    console.log("Play: magaza metinleri ve gorseller guncellendi");
  });
}

const [cmd, ...args] = process.argv.slice(2);
if (cmd === "release" && args.length >= 2) await release(args[0], args[1], args[2] || "production");
else if (cmd === "listing") await listing();
else {
  console.error("kullanim: bun scripts/play.js release <aab> <surum> [kanal] | listing");
  process.exit(1);
}
