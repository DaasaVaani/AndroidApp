import { readFile, writeFile, mkdir } from "node:fs/promises";
import { strictYaml } from "../shared/tools/generate-catalog.mjs";
import { validateAndroidSettings } from "./validate-config.mjs";
const root = new URL("../", import.meta.url);
const config = strictYaml(await readFile(new URL("settings/android.yml", root), "utf8"));
validateAndroidSettings(config);
const bounds = strictYaml(await readFile(new URL("shared/settings/publication-policy.yml", root), "utf8"));
const search = strictYaml(await readFile(new URL("shared/settings/search.yml", root), "utf8"));
const personal = strictYaml(await readFile(new URL("shared/settings/personal-transfer.yml", root), "utf8"));
const backup = strictYaml(await readFile(new URL("shared/settings/full-backup.yml", root), "utf8"));
if (backup.formatVersion !== 1 || backup.android.sourceModel !== "nativeRoom7" ||
    backup.android.fontUnit !== "sp" || backup.android.fontMin !== config.ui.readerFontMinSp ||
    backup.android.fontMax !== config.ui.readerFontMaxSp || backup.android.fontStep !== config.ui.readerFontStepSp ||
    !["maxBackupEncodedBytes", "maxBackupDecodedBytes"].every(key => Number.isSafeInteger(personal[key]) && personal[key] > 0))
  throw new Error("Unsupported full backup profile");
const defaults = strictYaml(await readFile(new URL("shared/settings/defaults.yml", root), "utf8"));
if (typeof defaults.recentHistoryEnabled !== "boolean") throw new Error("Invalid recent history default");
if (defaults.readingDensity !== "normal" ||
    JSON.stringify(defaults.supportedReadingDensities) !== JSON.stringify(["compact", "normal", "relaxed"]))
  throw new Error("Unsupported Shared reading defaults");
if (personal.formatVersion !== 1 || !["maxFavorites", "maxPersonalStoredBytes", "maxListEncodedBytes",
    "maxListDecodedBytes", "maxSharedUrlAscii", "maxListNameCodePoints", "maxEntryTextCodePoints", "maxEntriesPerList",
    "maxLists", "maxTotalEntries", "maxRecents", "maxLearningLabels"]
    .every(key => Number.isSafeInteger(personal[key]) && personal[key] > 0)) throw new Error("Invalid personal settings");
if (!search || search.formatVersion !== 1 || !Number.isInteger(search.maxQueryCodePoints) ||
    !Number.isInteger(search.maxTerms) || !Number.isInteger(search.pageSize)) throw new Error("Invalid trusted search settings");
const ui = config.ui;
const lyricBounds = Object.fromEntries(Object.entries(config).filter(([key]) => key.startsWith("lyric") && key !== "lyricDescriptorUrl"));
for (const [native, portable] of Object.entries({
  lyricDescriptorBytes: "maxDescriptorBytes", lyricManifestEncodedBytes: "maxManifestEncodedBytes",
  lyricManifestDecodedBytes: "maxManifestDecodedBytes", lyricItemEncodedBytes: "maxItemEncodedBytes",
  lyricItemDecodedBytes: "maxItemDecodedBytes",
})) {
  if (!Number.isSafeInteger(bounds[portable]) || lyricBounds[native] > bounds[portable])
    throw new Error(`Native lyric bound exceeds portable policy: ${native}`);
}
await mkdir(new URL("build/generated", root), { recursive: true });
await mkdir(new URL("build/generated/kotlin", root), { recursive: true });
await writeFile(new URL("build/generated/kotlin/PersonalPolicy.kt", root),
  `package \`in\`.daasavaani.core\n\n// Generated from pinned Shared settings.\n` +
  `object PersonalPolicy {\n    const val recentHistoryEnabled = ${defaults.recentHistoryEnabled}\n` +
  `    const val maxRecents = ${personal.maxRecents}\n` +
  `    const val maxLearningLabels = ${personal.maxLearningLabels}\n}\n`);
await writeFile(new URL("build/generated/kotlin/ReadingPolicy.kt", root),
  `package \`in\`.daasavaani.core\n\n// Generated from owned native settings and pinned Shared defaults.\n` +
  `object ReadingPolicy {\n` +
  Object.entries(ui).filter(([key]) => key.startsWith("reader") || key.startsWith("density"))
    .map(([key, value]) => `    const val ${key} = ${value}\n`).join("") +
  `    const val defaultDensity = "${defaults.readingDensity}"\n` +
  `    val supportedDensities = listOf(${defaults.supportedReadingDensities.map(value => JSON.stringify(value)).join(", ")})\n}\n`);
await writeFile(new URL("build/generated/kotlin/BackupPolicy.kt", root),
  `package \`in\`.daasavaani.core\n\n// Generated from pinned Shared backup domains and limits.\nobject BackupPolicy {\n` +
  ["maxBackupEncodedBytes", "maxBackupDecodedBytes", "maxPersonalStoredBytes", "maxLists", "maxTotalEntries",
    "maxFavorites", "maxLearningLabels", "maxRecents", "maxEntriesPerList", "maxListNameCodePoints", "maxEntryTextCodePoints"]
    .map(key => `    const val ${key} = ${personal[key]}\n`).join("") +
  `    const val fontMin = ${backup.android.fontMin}\n    const val fontMax = ${backup.android.fontMax}\n` +
  `    const val fontStep = ${backup.android.fontStep}\n}\n`);
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
  `<string name="backup_file_name" translatable="false">${xml(config.backupFileName)}</string>\n` +
  Object.entries({ ...bounds, spacingDp: ui.spacingDp, contentPaddingDp: ui.contentPaddingDp,
    connectTimeoutMs: config.connectTimeoutMs, readTimeoutMs: config.readTimeoutMs,
    trustedMaxQueryCodePoints: search.maxQueryCodePoints, trustedMaxTerms: search.maxTerms, trustedMaxPageSize: search.pageSize,
    ...personal, ...lyricBounds, listCompressionLevel: config.listCompressionLevel })
    .filter(([, value]) => typeof value === "number")
    .map(([key, value]) => `<integer name="${key.replace(/[A-Z]/g, c => "_" + c.toLowerCase())}">${value}</integer>`).join("\n") +
  "\n" + Object.entries(config.ui.colors).map(([key, value]) => `<color name="theme_${key}">${xml(value)}</color>`).join("\n") +
  "\n</resources>\n");
