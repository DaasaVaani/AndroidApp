const uiNumbers = ["spacingDp", "contentPaddingDp", "readerFontSizeSp", "readerLineHeightSp", "readerLineSpacingDp",
  "readerFontMinSp", "readerFontMaxSp", "readerFontStepSp", "densityCompactPercent", "densityNormalPercent", "densityRelaxedPercent"];
const lyricNumbers = ["lyricDescriptorBytes", "lyricManifestEncodedBytes", "lyricManifestDecodedBytes",
  "lyricItemEncodedBytes", "lyricItemDecodedBytes", "lyricCatalogItems", "lyricLines", "lyricTextCodePoints",
  "lyricTags", "lyricQueryCodePoints", "lyricJsonDepth", "lyricCallTimeoutMs", "lyricPreparedEncodedBytes", "lyricPreparedDecodedBytes"];
const rootKeys = ["formatVersion", "applicationId", "minSdk", "compileSdk", "targetSdk", "versionCode", "versionName",
  "descriptorUrl", "connectTimeoutMs", "readTimeoutMs", "fixtureOnly", "lyricDescriptorUrl", ...lyricNumbers,
  "canonicalBaseUrl", "listLinkPath", "listFileName", "backupFileName", "listCompressionLevel", "ui"];
function exact(value, keys) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    Reflect.ownKeys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}
const positive = value => Number.isSafeInteger(value) && value > 0 && value <= 2147483647;
function endpoint(value, protocols) {
  if (typeof value !== "string") throw new Error("Invalid native endpoint");
  const url = new URL(value);
  if (!protocols.includes(url.protocol) || url.username || url.password || url.href !== value ||
      value.includes("?") || value.includes("#") || !url.pathname.endsWith("/latest.json")) {
    throw new Error("Invalid native endpoint");
  }
  return url;
}
export function validateAndroidSettings(config) {
  if (!exact(config, rootKeys) || config.formatVersion !== 1 || config.fixtureOnly !== true ||
      Object.values(config).some(value => typeof value === "string" && /[\x00-\x20\x7f]/.test(value)) ||
      config.applicationId !== "in.daasavaani.app" || config.minSdk !== 26 ||
      !["compileSdk", "targetSdk", "versionCode", "connectTimeoutMs", "readTimeoutMs", ...lyricNumbers]
        .every(key => positive(config[key])) ||
      config.targetSdk < config.minSdk || config.compileSdk < config.targetSdk ||
      typeof config.versionName !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(config.versionName) ||
      !exact(config.ui, [...uiNumbers, "colors"]) || !uiNumbers.every(key => positive(config.ui[key])) ||
      !exact(config.ui.colors, ["background", "surface", "text", "primary"]) ||
      !Object.values(config.ui.colors).every(value => typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value))) {
    throw new Error("Invalid Android fixture settings");
  }
  const ui = config.ui;
  if (ui.readerFontMinSp >= ui.readerFontMaxSp || ui.readerFontMinSp > ui.readerFontSizeSp ||
      ui.readerFontSizeSp > ui.readerFontMaxSp ||
      (ui.readerFontMaxSp - ui.readerFontMinSp) % ui.readerFontStepSp !== 0 ||
      (ui.readerFontSizeSp - ui.readerFontMinSp) % ui.readerFontStepSp !== 0 ||
      ui.readerLineHeightSp < ui.readerFontSizeSp || ui.densityNormalPercent !== 100 ||
      ui.densityCompactPercent >= ui.densityNormalPercent || ui.densityRelaxedPercent <= ui.densityNormalPercent) {
    throw new Error("Invalid native reading settings");
  }
  endpoint(config.descriptorUrl, ["http:", "https:"]);
  endpoint(config.lyricDescriptorUrl, ["https:"]);
  const canonical = new URL(config.canonicalBaseUrl);
  if (canonical.protocol !== "https:" || canonical.username || canonical.password ||
      canonical.href !== config.canonicalBaseUrl || config.canonicalBaseUrl.includes("?") ||
      config.canonicalBaseUrl.includes("#") || canonical.pathname !== "/" ||
      typeof config.listLinkPath !== "string" || !/^[A-Za-z0-9_-]+\/$/.test(config.listLinkPath) ||
      typeof config.listFileName !== "string" || !/^[A-Za-z0-9._-]+\.dvlist\.json\.gz$/.test(config.listFileName) ||
      typeof config.backupFileName !== "string" || !/^[A-Za-z0-9._-]+\.dvbackup\.json\.gz$/.test(config.backupFileName) ||
      !Number.isSafeInteger(config.listCompressionLevel) || config.listCompressionLevel < 0 || config.listCompressionLevel > 9) {
    throw new Error("Invalid list transport settings");
  }
}
