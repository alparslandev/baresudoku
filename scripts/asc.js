import { readFileSync } from "fs";
import { basename } from "path";
import { createSign, createHash } from "crypto";

const BUNDLE_ID = "com.baresudoku";
const API = "https://api.appstoreconnect.apple.com/v1";
const STORE = new URL("../ios/store/", import.meta.url);
const PLATFORM = { ios: "IOS", mac: "MAC_OS", tv: "TV_OS", vision: "VISION_OS" };
const DISPLAY_TYPES = { IOS: ["APP_IPHONE_69", "APP_IPAD_PRO_3GEN_129"], MAC_OS: ["APP_DESKTOP"], TV_OS: ["APP_APPLE_TV"], VISION_OS: ["APP_APPLE_VISION_PRO"] };
const FALLBACK_TYPE = { APP_IPHONE_69: "APP_IPHONE_67" };
const API_V2 = "https://api.appstoreconnect.apple.com/v2";
const SHOP = [
  {
    id: "com.baresudoku.supporter",
    kind: "NON_CONSUMABLE",
    label: "Supporter",
    price: "2.99",
    texts: { "en-US": { name: "Bare Sudoku Supporter", description: "Variants, color themes and the daily archive." }, tr: { name: "Bare Sudoku Destekçisi", description: "Varyantlar, renk temaları ve günlük arşiv." } },
  },
  {
    id: "com.baresudoku.tip.small",
    kind: "CONSUMABLE",
    label: "Small Tip",
    price: "0.99",
    texts: { "en-US": { name: "Small Tip", description: "A small thank you for Bare Sudoku." }, tr: { name: "Küçük Bahşiş", description: "Bare Sudoku'ya küçük bir teşekkür." } },
  },
  {
    id: "com.baresudoku.tip.medium",
    kind: "CONSUMABLE",
    label: "Medium Tip",
    price: "2.99",
    texts: { "en-US": { name: "Medium Tip", description: "A bigger thank you for Bare Sudoku." }, tr: { name: "Orta Bahşiş", description: "Bare Sudoku'ya büyük bir teşekkür." } },
  },
  {
    id: "com.baresudoku.tip.large",
    kind: "CONSUMABLE",
    label: "Large Tip",
    price: "4.99",
    texts: { "en-US": { name: "Large Tip", description: "A huge thank you for Bare Sudoku." }, tr: { name: "Büyük Bahşiş", description: "Bare Sudoku'ya kocaman bir teşekkür." } },
  },
];
const SHOP_BASE = "USA";
const SHOP_LIMITS = { name: 30, description: 45 };
const SHOP_SHOT = "inAppPurchaseAppStoreReviewScreenshots";
const SHOP_SHOT_LEAST = { short: 640, long: 920 };
const SHOP_READY = "READY_TO_SUBMIT";
const FIRST_PRICE = "${price1}";
const b64 = s => Buffer.from(s).toString("base64url");
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let cfg;
const config = () => (cfg ||= JSON.parse(readFileSync(`${process.env.HOME}/.baresudoku/asc.json`, "utf8")));

function token() {
  config();
  const now = Math.floor(Date.now() / 1000);
  const header = b64(JSON.stringify({ alg: "ES256", kid: cfg.keyId, typ: "JWT" }));
  const claims = b64(JSON.stringify({ iss: cfg.issuerId, iat: now, exp: now + 1200, aud: "appstoreconnect-v1" }));
  const signer = createSign("SHA256");
  signer.update(`${header}.${claims}`);
  return `${header}.${claims}.${signer.sign({ key: readFileSync(cfg.key, "utf8"), dsaEncoding: "ieee-p1363" }, "base64url")}`;
}

async function api(method, path, body) {
  const r = await fetch(path.startsWith("http") ? path : API + path, {
    method,
    headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} ${r.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

const state = v => v.attributes.appVersionState || v.attributes.appStoreState;
const EDITABLE = new Set(["PREPARE_FOR_SUBMISSION", "READY_FOR_REVIEW", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"]);
const LIVE = new Set(["READY_FOR_SALE", "READY_FOR_DISTRIBUTION", "PENDING_DEVELOPER_RELEASE", "PROCESSING_FOR_DISTRIBUTION"]);

async function appId() {
  const r = await api("GET", `/apps?filter[bundleId]=${BUNDLE_ID}`);
  if (!r.data.length) throw new Error("App Store Connect'te uygulama kaydi yok");
  return r.data[0].id;
}

async function versions(app, platform) {
  return (await api("GET", `/apps/${app}/appStoreVersions?filter[platform]=${platform}&limit=20`)).data;
}

async function versionFor(app, platform, version) {
  const all = await versions(app, platform);
  const same = all.find(v => v.attributes.versionString === version);
  if (same) return same.id;
  const editable = all.find(v => EDITABLE.has(state(v)));
  if (editable) {
    await api("PATCH", `/appStoreVersions/${editable.id}`, { data: { type: "appStoreVersions", id: editable.id, attributes: { versionString: version } } });
    return editable.id;
  }
  const created = await api("POST", "/appStoreVersions", {
    data: {
      type: "appStoreVersions",
      attributes: { platform, versionString: version, releaseType: "AFTER_APPROVAL" },
      relationships: { app: { data: { type: "apps", id: app } } },
    },
  });
  return created.data.id;
}

async function waitBuild(app, platform, version, build) {
  for (let i = 0; i < 80; i++) {
    const r = await api("GET", `/builds?filter[app]=${app}&filter[preReleaseVersion.platform]=${platform}&filter[preReleaseVersion.version]=${version}&filter[version]=${build}&sort=-uploadedDate&limit=1`);
    const b = r.data[0];
    if (b) {
      const s = b.attributes.processingState;
      if (s === "VALID") return b.id;
      if (s === "FAILED" || s === "INVALID") throw new Error(`Derleme ${build} islenemedi: ${s}`);
    }
    console.log(`${platform} derleme ${build} isleniyor, bekleniyor...`);
    await sleep(30000);
  }
  throw new Error("Derleme 40 dakikada islenmedi");
}

async function localizations(vid) {
  return (await api("GET", `/appStoreVersions/${vid}/appStoreVersionLocalizations`)).data;
}

async function setWhatsNew(vid) {
  const notes = JSON.parse(readFileSync(new URL("../release-notes.json", import.meta.url), "utf8"));
  for (const loc of await localizations(vid)) {
    const text = notes[loc.attributes.locale.split("-")[0]];
    if (text) await api("PATCH", `/appStoreVersionLocalizations/${loc.id}`, { data: { type: "appStoreVersionLocalizations", id: loc.id, attributes: { whatsNew: text } } });
  }
}

async function cancelSubmissions(app, platform) {
  const active = (await api("GET", `/reviewSubmissions?filter[app]=${app}&filter[platform]=${platform}&filter[state]=WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES`)).data;
  for (const s of active) {
    await api("PATCH", `/reviewSubmissions/${s.id}`, { data: { type: "reviewSubmissions", id: s.id, attributes: { canceled: true } } });
    console.log(`${platform}: onceki gonderim (${s.attributes.state}) iptal edildi`);
  }
  if (active.length) await sleep(5000);
}

async function submit(version, build, platform) {
  const app = await appId();
  const buildId = await waitBuild(app, platform, version, build);
  await cancelSubmissions(app, platform);
  const vid = await versionFor(app, platform, version);
  await api("PATCH", `/appStoreVersions/${vid}/relationships/build`, { data: { type: "builds", id: buildId } });
  const listing = JSON.parse(readFileSync(new URL("listing.json", STORE), "utf8"));
  await appInfo(app, listing);
  await setCopyright(vid, listing);
  await reviewDetail(vid, listing);
  if ((await versions(app, platform)).some(v => LIVE.has(state(v)) && v.id !== vid)) await setWhatsNew(vid);
  const open = (await api("GET", `/reviewSubmissions?filter[app]=${app}&filter[platform]=${platform}&filter[state]=READY_FOR_REVIEW`)).data[0];
  const sub = open ? open.id : (await api("POST", "/reviewSubmissions", {
    data: { type: "reviewSubmissions", attributes: { platform }, relationships: { app: { data: { type: "apps", id: app } } } },
  })).data.id;
  if (platform === "IOS") await attachShop(app);
  await api("POST", "/reviewSubmissionItems", {
    data: {
      type: "reviewSubmissionItems",
      relationships: {
        reviewSubmission: { data: { type: "reviewSubmissions", id: sub } },
        appStoreVersion: { data: { type: "appStoreVersions", id: vid } },
      },
    },
  });
  await api("PATCH", `/reviewSubmissions/${sub}`, { data: { type: "reviewSubmissions", id: sub, attributes: { submitted: true } } });
  console.log(`App Store ${platform}: ${version} (${build}) incelemeye gonderildi`);
}

async function createSet(locId, displayType) {
  const body = type => ({
    data: { type: "appScreenshotSets", attributes: { screenshotDisplayType: type }, relationships: { appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: locId } } } },
  });
  try {
    return (await api("POST", "/appScreenshotSets", body(displayType))).data;
  } catch (e) {
    if (!FALLBACK_TYPE[displayType]) throw e;
    return (await api("POST", "/appScreenshotSets", body(FALLBACK_TYPE[displayType]))).data;
  }
}

async function uploadScreenshots(locId, displayTypes, files) {
  for (const set of (await api("GET", `/appStoreVersionLocalizations/${locId}/appScreenshotSets`)).data) await api("DELETE", `/appScreenshotSets/${set.id}`);
  for (const displayType of displayTypes) {
    const names = files[displayType] || [];
    if (!names.length) continue;
    const set = await createSet(locId, displayType);
    for (const name of names) {
      const bytes = readFileSync(new URL(name, STORE));
      const shot = (await api("POST", "/appScreenshots", {
        data: { type: "appScreenshots", attributes: { fileName: basename(name), fileSize: bytes.length }, relationships: { appScreenshotSet: { data: { type: "appScreenshotSets", id: set.id } } } },
      })).data;
      for (const op of shot.attributes.uploadOperations) {
        const headers = {};
        for (const h of op.requestHeaders) headers[h.name] = h.value;
        const r = await fetch(op.url, { method: op.method, headers, body: bytes.subarray(op.offset, op.offset + op.length) });
        if (!r.ok) throw new Error(`ekran goruntusu yuklenemedi ${r.status}: ${await r.text()}`);
      }
      await api("PATCH", `/appScreenshots/${shot.id}`, {
        data: { type: "appScreenshots", id: shot.id, attributes: { uploaded: true, sourceFileChecksum: createHash("md5").update(bytes).digest("hex") } },
      });
    }
  }
}

async function patchIfEditable(path, body, what) {
  try {
    await api("PATCH", path, body);
  } catch (e) {
    if (!/INVALID_STATE|STATE_ERROR/.test(String(e.message))) throw e;
    console.log(`${what}: kilitli (inceleme ya da yayinda), atlandi`);
  }
}

async function appInfo(app, listing) {
  const infos = (await api("GET", `/apps/${app}/appInfos`)).data;
  const info = infos.find(i => EDITABLE.has(i.attributes.state || i.attributes.appStoreState)) || infos[0];
  await patchIfEditable(`/appInfos/${info.id}`, {
    data: {
      type: "appInfos",
      id: info.id,
      relationships: {
        primaryCategory: { data: { type: "appCategories", id: "GAMES" } },
        primarySubcategoryOne: { data: { type: "appCategories", id: "GAMES_PUZZLE" } },
        primarySubcategoryTwo: { data: { type: "appCategories", id: "GAMES_BOARD" } },
      },
    },
  }, "kategori");
  const infoLocs = (await api("GET", `/appInfos/${info.id}/appInfoLocalizations`)).data;
  for (const [locale, l] of Object.entries(listing.locales)) {
    const attributes = { name: l.name, subtitle: l.subtitle, privacyPolicyUrl: l.privacyPolicyUrl, privacyPolicyText: l.privacyPolicyText };
    const existing = infoLocs.find(x => x.attributes.locale === locale);
    if (existing) await patchIfEditable(`/appInfoLocalizations/${existing.id}`, { data: { type: "appInfoLocalizations", id: existing.id, attributes } }, "ad ve alt baslik " + locale);
    else await api("POST", "/appInfoLocalizations", { data: { type: "appInfoLocalizations", attributes: { locale, ...attributes }, relationships: { appInfo: { data: { type: "appInfos", id: info.id } } } } });
  }
  const age = (await api("GET", `/appInfos/${info.id}/ageRatingDeclaration`)).data;
  await patchIfEditable(`/ageRatingDeclarations/${age.id}`, {
    data: {
      type: "ageRatingDeclarations",
      id: age.id,
      attributes: {
        alcoholTobaccoOrDrugUseOrReferences: "NONE", contests: "NONE", gambling: false, gamblingSimulated: "NONE",
        gunsOrOtherWeapons: "NONE", horrorOrFearThemes: "NONE", matureOrSuggestiveThemes: "NONE",
        medicalOrTreatmentInformation: "NONE", profanityOrCrudeHumor: "NONE", sexualContentGraphicAndNudity: "NONE",
        sexualContentOrNudity: "NONE", violenceCartoonOrFantasy: "NONE", violenceRealistic: "NONE",
        violenceRealisticProlongedGraphicOrSadistic: "NONE",
        advertising: false, ageAssurance: false, healthOrWellnessTopics: false, lootBox: false,
        messagingAndChat: false, parentalControls: false, socialMedia: false, unrestrictedWebAccess: false,
        userGeneratedContent: false, ageRatingOverrideV2: "NONE", koreaAgeRatingOverride: "NONE",
      },
    },
  }, "yas derecesi");
}

async function setCopyright(vid, listing) {
  await api("PATCH", `/appStoreVersions/${vid}`, { data: { type: "appStoreVersions", id: vid, attributes: { copyright: listing.copyright } } });
}

async function reviewDetail(vid, listing) {
  const contact = config().review;
  if (!contact) { console.log("Inceleme iletisim bilgisi yok (~/.baresudoku/asc.json review), atlandi"); return; }
  const attributes = {
    contactFirstName: contact.firstName, contactLastName: contact.lastName, contactPhone: contact.phone, contactEmail: contact.email,
    demoAccountRequired: false, notes: listing.reviewNotes,
  };
  const existing = (await api("GET", `/appStoreVersions/${vid}/appStoreReviewDetail`)).data;
  if (existing) await api("PATCH", `/appStoreReviewDetails/${existing.id}`, { data: { type: "appStoreReviewDetails", id: existing.id, attributes } });
  else await api("POST", "/appStoreReviewDetails", { data: { type: "appStoreReviewDetails", attributes, relationships: { appStoreVersion: { data: { type: "appStoreVersions", id: vid } } } } });
}

async function review(version, platform) {
  const listing = JSON.parse(readFileSync(new URL("listing.json", STORE), "utf8"));
  await reviewDetail(await versionFor(await appId(), platform, version), listing);
  console.log(`App Store ${platform}: ${version} icin inceleme iletisim bilgisi ve notu yuklendi`);
}

async function metadata(version, platform) {
  const listing = JSON.parse(readFileSync(new URL("listing.json", STORE), "utf8"));
  const app = await appId();
  await api("PATCH", `/apps/${app}`, { data: { type: "apps", id: app, attributes: { contentRightsDeclaration: "DOES_NOT_USE_THIRD_PARTY_CONTENT" } } });
  await appInfo(app, listing);
  const vid = await versionFor(app, platform, version);
  await setCopyright(vid, listing);
  const locs = await localizations(vid);
  for (const [locale, l] of Object.entries(listing.locales)) {
    const attributes = { description: l.description, keywords: l.keywords, supportUrl: l.supportUrl, marketingUrl: l.marketingUrl, promotionalText: l.promotionalText };
    let loc = locs.find(x => x.attributes.locale === locale);
    if (loc) await api("PATCH", `/appStoreVersionLocalizations/${loc.id}`, { data: { type: "appStoreVersionLocalizations", id: loc.id, attributes } });
    else loc = (await api("POST", "/appStoreVersionLocalizations", { data: { type: "appStoreVersionLocalizations", attributes: { locale, ...attributes }, relationships: { appStoreVersion: { data: { type: "appStoreVersions", id: vid } } } } })).data;
    await uploadScreenshots(loc.id, DISPLAY_TYPES[platform], listing.screenshots);
  }
  await reviewDetail(vid, listing);
  console.log(`App Store ${platform}: ${version} icin metinler, kategori, yas derecesi, ekran goruntuleri ve inceleme bilgileri yuklendi. Elle kalan: App Privacy (Data Not Collected), fiyat (Free).`);
}

async function related(path) {
  try {
    return (await api("GET", path)).data;
  } catch (e) {
    if (String(e.message).includes(" 404: ")) return null;
    throw e;
  }
}

async function shopState(app) {
  return (await api("GET", `/apps/${app}/inAppPurchasesV2?limit=200`)).data;
}

async function shopPages(item) {
  return (await api("GET", `${API_V2}/inAppPurchases/${item.id}/inAppPurchaseLocalizations?limit=200`)).data;
}

async function shopPrices(item) {
  let listed;
  try {
    listed = await api("GET", `/inAppPurchasePriceSchedules/${item.id}/manualPrices?include=inAppPurchasePricePoint,territory&limit=50`);
  } catch (e) {
    if (String(e.message).includes(" 404: ")) return [];
    throw e;
  }
  const points = new Map((listed.included || []).filter(x => x.type === "inAppPurchasePricePoints").map(x => [x.id, x.attributes.customerPrice]));
  return listed.data.map(p => `${p.relationships?.territory?.data?.id || "?"} ${points.get(p.relationships?.inAppPurchasePricePoint?.data?.id) || "?"}`);
}

async function shopReach(item) {
  const reach = await related(`${API_V2}/inAppPurchases/${item.id}/inAppPurchaseAvailability`);
  if (!reach) return null;
  const places = await api("GET", `/inAppPurchaseAvailabilities/${reach.id}/availableTerritories?limit=200`);
  return { count: places.meta?.paging?.total ?? places.data.length, future: reach.attributes.availableInNewTerritories };
}

async function shopShot(item) {
  return related(`${API_V2}/inAppPurchases/${item.id}/appStoreReviewScreenshot`);
}

async function shop() {
  const listed = await shopState(await appId());
  for (const product of SHOP) {
    const item = listed.find(x => x.attributes.productId === product.id);
    if (!item) { console.log(`urun ${product.id}: yok`); continue; }
    console.log(`urun ${product.id}: ${item.attributes.name}, ${item.attributes.inAppPurchaseType}, ${item.attributes.state}`);
    console.log(`  metin: ${(await shopPages(item)).map(p => `${p.attributes.locale} "${p.attributes.name}" / "${p.attributes.description}"`).join(", ") || "yok"}`);
    console.log(`  fiyat: ${(await shopPrices(item)).join(", ") || "yok"} (beklenen ${SHOP_BASE} ${product.price})`);
    const reach = await shopReach(item);
    console.log(`  satis yeri: ${reach ? `${reach.count} bolge, yeni bolgeler ${reach.future ? "dahil" : "haric"}` : "yok"}`);
    const shot = await shopShot(item);
    console.log(`  inceleme resmi: ${shot ? `${shot.attributes.fileName} ${shot.attributes.fileSize} bayt, ${shot.attributes.assetDeliveryState?.state || "?"}` : "yok"}`);
  }
}

async function openShop() {
  for (const product of SHOP) {
    for (const [locale, words] of Object.entries(product.texts)) {
      for (const [field, limit] of Object.entries(SHOP_LIMITS)) {
        if ([...words[field]].length > limit) throw new Error(`${product.id} (${locale}): ${field} ${[...words[field]].length} karakter, sinir ${limit}`);
      }
    }
  }
  const app = await appId();
  const listed = await shopState(app);
  const places = (await api("GET", "/territories?limit=200")).data.map(p => ({ type: "territories", id: p.id }));
  for (const product of SHOP) {
    let item = listed.find(x => x.attributes.productId === product.id);
    if (item) console.log(`urun zaten var: ${product.id}`);
    else {
      item = (await api("POST", `${API_V2}/inAppPurchases`, {
        data: { type: "inAppPurchases", attributes: { name: product.label, productId: product.id, inAppPurchaseType: product.kind }, relationships: { app: { data: { type: "apps", id: app } } } },
      })).data;
      console.log(`urun acildi: ${product.id}`);
    }
    const link = { data: { type: "inAppPurchases", id: item.id } };
    const pages = await shopPages(item);
    for (const [locale, words] of Object.entries(product.texts)) {
      if (pages.some(p => p.attributes.locale === locale)) console.log(`${product.id} (${locale}): metin zaten var`);
      else {
        await api("POST", "/inAppPurchaseLocalizations", { data: { type: "inAppPurchaseLocalizations", attributes: { locale, ...words }, relationships: { inAppPurchaseV2: link } } });
        console.log(`${product.id} (${locale}): metin eklendi`);
      }
    }
    if ((await shopPrices(item)).length) console.log(`${product.id}: fiyat zaten var`);
    else {
      const points = (await api("GET", `${API_V2}/inAppPurchases/${item.id}/pricePoints?filter[territory]=${SHOP_BASE}&limit=8000`)).data;
      const point = points.find(p => p.attributes.customerPrice === product.price);
      if (!point) throw new Error(`${product.id}: ${SHOP_BASE} icin ${product.price} fiyat basamagi yok`);
      await api("POST", "/inAppPurchasePriceSchedules", {
        data: {
          type: "inAppPurchasePriceSchedules",
          relationships: { inAppPurchase: link, baseTerritory: { data: { type: "territories", id: SHOP_BASE } }, manualPrices: { data: [{ type: "inAppPurchasePrices", id: FIRST_PRICE }] } },
        },
        included: [{ type: "inAppPurchasePrices", id: FIRST_PRICE, attributes: { startDate: null }, relationships: { inAppPurchaseV2: link, inAppPurchasePricePoint: { data: { type: "inAppPurchasePricePoints", id: point.id } } } }],
      });
      console.log(`${product.id}: fiyat ${SHOP_BASE} ${product.price}`);
    }
    if (await shopReach(item)) console.log(`${product.id}: satis yeri zaten var`);
    else {
      await api("POST", "/inAppPurchaseAvailabilities", { data: { type: "inAppPurchaseAvailabilities", attributes: { availableInNewTerritories: true }, relationships: { inAppPurchase: link, availableTerritories: { data: places } } } });
      console.log(`${product.id}: ${places.length} bolgede satisa acildi`);
    }
  }
}

function shotFile(path) {
  const bytes = readFileSync(path);
  if (bytes.subarray(1, 4).toString() !== "PNG") throw new Error(`${path}: PNG degil`);
  const width = bytes.readUInt32BE(16);
  const height = bytes.readUInt32BE(20);
  if (Math.min(width, height) < SHOP_SHOT_LEAST.short || Math.max(width, height) < SHOP_SHOT_LEAST.long) throw new Error(`${path}: ${width}x${height} kucuk`);
  return { fileName: basename(path), bytes, checksum: createHash("md5").update(bytes).digest("hex"), width, height };
}

async function shopShots(path) {
  const file = shotFile(path);
  const listed = await shopState(await appId());
  for (const product of SHOP) {
    const item = listed.find(x => x.attributes.productId === product.id);
    if (!item) throw new Error(`urun yok: ${product.id}; once: bun scripts/asc.js shop open`);
    const old = await shopShot(item);
    if (old && old.attributes.sourceFileChecksum === file.checksum && old.attributes.assetDeliveryState?.state === "COMPLETE") {
      console.log(`${product.id} inceleme resmi: zaten ayni`);
      continue;
    }
    if (old) await api("DELETE", `/${SHOP_SHOT}/${old.id}`);
    const shot = (await api("POST", `/${SHOP_SHOT}`, { data: { type: SHOP_SHOT, attributes: { fileName: file.fileName, fileSize: file.bytes.length }, relationships: { inAppPurchaseV2: { data: { type: "inAppPurchases", id: item.id } } } } })).data;
    for (const op of shot.attributes.uploadOperations) {
      const headers = {};
      for (const h of op.requestHeaders) headers[h.name] = h.value;
      const r = await fetch(op.url, { method: op.method, headers, body: file.bytes.subarray(op.offset, op.offset + op.length) });
      if (!r.ok) throw new Error(`inceleme resmi yuklenemedi ${r.status}: ${await r.text()}`);
    }
    await api("PATCH", `/${SHOP_SHOT}/${shot.id}`, { data: { type: SHOP_SHOT, id: shot.id, attributes: { uploaded: true, sourceFileChecksum: file.checksum } } });
    for (let i = 0; i < 40; i++) {
      const state = (await api("GET", `/${SHOP_SHOT}/${shot.id}`)).data.attributes.assetDeliveryState?.state;
      if (state === "COMPLETE") break;
      if (state === "FAILED") throw new Error(`${product.id} inceleme resmi islenemedi`);
      await sleep(3000);
    }
    console.log(`${product.id} inceleme resmi: ${file.width}x${file.height} yuklendi`);
  }
}

async function attachShop(app) {
  const listed = await shopState(app);
  for (const product of SHOP) {
    const item = listed.find(x => x.attributes.productId === product.id);
    if (!item) { console.log(`${product.id}: hesapta yok, gonderime girmedi`); continue; }
    if (item.attributes.state !== SHOP_READY) { console.log(`${product.id}: ${item.attributes.state}, gonderime eklenmedi`); continue; }
    await api("POST", "/inAppPurchaseSubmissions", { data: { type: "inAppPurchaseSubmissions", relationships: { inAppPurchaseV2: { data: { type: "inAppPurchases", id: item.id } } } } });
    console.log(`${product.id}: surumle birlikte incelemeye eklendi`);
  }
}

const [cmd, ...args] = process.argv.slice(2);
const platform = PLATFORM[args[cmd === "submit" ? 2 : 1] || "ios"];
if (cmd === "submit" && args.length >= 2 && platform) await submit(args[0], args[1], platform);
else if (cmd === "metadata" && args.length >= 1 && platform) await metadata(args[0], platform);
else if (cmd === "review" && args.length >= 1 && platform) await review(args[0], platform);
else if (cmd === "shop" && args[0] === "open") await openShop();
else if (cmd === "shop" && args[0] === "shots" && args[1]) await shopShots(args[1]);
else if (cmd === "shop" && !args.length) await shop();
else {
  console.error("kullanim: bun scripts/asc.js submit <surum> <derleme> [ios|mac|tv|vision] | metadata <surum> [ios|mac|tv|vision] | review <surum> [ios|mac|tv|vision] | shop [open | shots <png>]");
  process.exit(1);
}
