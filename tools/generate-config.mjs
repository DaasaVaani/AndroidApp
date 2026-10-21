import { readFile, writeFile, mkdir } from "node:fs/promises";
import { strictYaml } from "../shared/tools/generate-catalog.mjs";
const root = new URL("../", import.meta.url);
const config = strictYaml(await readFile(new URL("settings/android.yml", root), "utf8"));
const bounds = strictYaml(await readFile(new URL("shared/settings/publication-policy.yml", root), "utf8"));
const search = strictYaml(await readFile(new URL("shared/settings/search.yml", root), "utf8"));
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
await mkdir(new URL("build/generated", root), { recursive: true });
await writeFile(new URL("build/generated/config.properties", root),
  Object.entries(config).filter(([, value]) => typeof value !== "object")
    .map(([key, value]) => `${key}=${String(value).replaceAll("\\", "\\\\")}`).join("\n") + "\n");
const xml = value => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
await mkdir(new URL("app/build/generated/settings/values", root), { recursive: true });
await writeFile(new URL("app/build/generated/settings/values/config.xml", root),
  `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n` +
  `<string name="descriptor_url" translatable="false">${xml(config.descriptorUrl)}</string>\n` +
  Object.entries({ ...bounds, ...config.ui, connectTimeoutMs: config.connectTimeoutMs, readTimeoutMs: config.readTimeoutMs,
    trustedMaxQueryCodePoints: search.maxQueryCodePoints, trustedMaxTerms: search.maxTerms, trustedMaxPageSize: search.pageSize })
    .filter(([, value]) => typeof value === "number")
    .map(([key, value]) => `<integer name="${key.replace(/[A-Z]/g, c => "_" + c.toLowerCase())}">${value}</integer>`).join("\n") +
  "\n" + Object.entries(config.ui.colors).map(([key, value]) => `<color name="theme_${key}">${xml(value)}</color>`).join("\n") +
  "\n</resources>\n");
