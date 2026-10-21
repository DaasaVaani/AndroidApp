import { readFile, writeFile, mkdir } from "node:fs/promises";
import { strictYaml } from "../shared/tools/generate-catalog.mjs";
const root = new URL("../", import.meta.url);
const config = strictYaml(await readFile(new URL("settings/android.yml", root), "utf8"));
const bounds = strictYaml(await readFile(new URL("shared/settings/publication-policy.yml", root), "utf8"));
const search = strictYaml(await readFile(new URL("shared/settings/search.yml", root), "utf8"));
const personal = strictYaml(await readFile(new URL("shared/settings/personal-transfer.yml", root), "utf8"));
if (personal.formatVersion !== 1 || !["maxFavorites", "maxPersonalStoredBytes", "maxListEncodedBytes",
    "maxListDecodedBytes", "maxSharedUrlAscii", "maxListNameCodePoints", "maxEntryTextCodePoints", "maxEntriesPerList"]
    .every(key => Number.isSafeInteger(personal[key]) && personal[key] > 0)) throw new Error("Invalid personal settings");
if (!search || search.formatVersion !== 1 || !Number.isInteger(search.maxQueryCodePoints) ||
    !Number.isInteger(search.maxTerms) || !Number.isInteger(search.pageSize)) throw new Error("Invalid trusted search settings");
if (!config || typeof config !== "object" || config.formatVersion !== 1 || config.fixtureOnly !== true ||
    config.applicationId !== "in.daasavaani.app" || config.minSdk !== 26 ||
    !Number.isInteger(config.compileSdk) || !Number.isInteger(config.targetSdk) ||
    !Number.isInteger(config.versionCode) || typeof config.versionName !== "string" ||
    !Number.isInteger(config.connectTimeoutMs) || !Number.isInteger(config.readTimeoutMs) ||
    typeof config.descriptorUrl !== "string" || !config.ui ||
    !["spacingDp", "contentPaddingDp", "readerFontSizeSp", "readerLineHeightSp"]
      .every(key => Number.isInteger(config.ui[key]) && config.ui[key] > 0) ||
    !config.ui.colors || !["background", "surface", "text", "primary"]
      .every(key => typeof config.ui.colors[key] === "string" && /^#[0-9a-fA-F]{6}$/.test(config.ui.colors[key]))) throw new Error("Invalid Android fixture settings");
const url = new URL(config.descriptorUrl);
if (!["http:", "https:"].includes(url.protocol)) throw new Error("Invalid descriptor URL");
const lyricUrl = new URL(config.lyricDescriptorUrl);
const lyricBounds = Object.fromEntries(Object.entries(config).filter(([key]) => key.startsWith("lyric") && key !== "lyricDescriptorUrl"));
if (lyricUrl.protocol !== "https:" || lyricUrl.username || lyricUrl.password || lyricUrl.search || lyricUrl.hash ||
    !lyricUrl.pathname.endsWith("/latest.json") ||
    !["lyricDescriptorBytes", "lyricManifestEncodedBytes", "lyricManifestDecodedBytes", "lyricItemEncodedBytes",
      "lyricItemDecodedBytes", "lyricCatalogItems", "lyricLines", "lyricTextCodePoints", "lyricTags", "lyricQueryCodePoints",
      "lyricJsonDepth", "lyricCallTimeoutMs", "lyricPreparedEncodedBytes", "lyricPreparedDecodedBytes"]
      .every(key => Number.isSafeInteger(lyricBounds[key]) && lyricBounds[key] > 0 && lyricBounds[key] <= 2147483647))
  throw new Error("Invalid prelaunch lyric settings");
for (const [native, portable] of Object.entries({
  lyricDescriptorBytes: "maxDescriptorBytes", lyricManifestEncodedBytes: "maxManifestEncodedBytes",
  lyricManifestDecodedBytes: "maxManifestDecodedBytes", lyricItemEncodedBytes: "maxItemEncodedBytes",
  lyricItemDecodedBytes: "maxItemDecodedBytes",
})) {
  if (!Number.isSafeInteger(bounds[portable]) || lyricBounds[native] > bounds[portable])
    throw new Error(`Native lyric bound exceeds portable policy: ${native}`);
}
const canonical = new URL(config.canonicalBaseUrl);
if (canonical.protocol !== "https:" || canonical.username || canonical.password || canonical.search || canonical.hash ||
    canonical.pathname !== "/" || typeof config.listLinkPath !== "string" || !/^[A-Za-z0-9_-]+\/$/.test(config.listLinkPath) ||
    typeof config.listFileName !== "string" || !/^[A-Za-z0-9._-]+\.dvlist\.json\.gz$/.test(config.listFileName) ||
    !Number.isInteger(config.listCompressionLevel) || config.listCompressionLevel < 0 || config.listCompressionLevel > 9)
  throw new Error("Invalid list transport settings");
await mkdir(new URL("build/generated", root), { recursive: true });
await writeFile(new URL("build/generated/config.properties", root),
  Object.entries(config).filter(([, value]) => typeof value !== "object")
    .map(([key, value]) => `${key}=${String(value).replaceAll("\\", "\\\\")}`).join("\n") + "\n");
const xml = value => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
await mkdir(new URL("app/build/generated/settings/values", root), { recursive: true });
await writeFile(new URL("app/build/generated/settings/values/config.xml", root),
  `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n` +
  `<string name="descriptor_url" translatable="false">${xml(config.descriptorUrl)}</string>\n` +
  `<string name="lyric_descriptor_url" translatable="false">${xml(config.lyricDescriptorUrl)}</string>\n` +
  `<string name="canonical_base_url" translatable="false">${xml(config.canonicalBaseUrl)}</string>\n` +
  `<string name="list_link_path" translatable="false">${xml(config.listLinkPath)}</string>\n` +
  `<string name="list_file_name" translatable="false">${xml(config.listFileName)}</string>\n` +
  Object.entries({ ...bounds, ...config.ui, connectTimeoutMs: config.connectTimeoutMs, readTimeoutMs: config.readTimeoutMs,
    trustedMaxQueryCodePoints: search.maxQueryCodePoints, trustedMaxTerms: search.maxTerms, trustedMaxPageSize: search.pageSize,
    ...personal, ...lyricBounds, listCompressionLevel: config.listCompressionLevel })
    .filter(([, value]) => typeof value === "number")
    .map(([key, value]) => `<integer name="${key.replace(/[A-Z]/g, c => "_" + c.toLowerCase())}">${value}</integer>`).join("\n") +
  "\n" + Object.entries(config.ui.colors).map(([key, value]) => `<color name="theme_${key}">${xml(value)}</color>`).join("\n") +
  "\n</resources>\n");
