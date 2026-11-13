import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { strictYaml } from "../shared/tools/generate-catalog.mjs";
import { validateAndroidSettings } from "./validate-config.mjs";

const config = strictYaml(readFileSync(new URL("../settings/android.yml", import.meta.url), "utf8"));
function paths(value, prefix = []) {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = [...prefix, key];
    return child && typeof child === "object" ? [path, ...paths(child, path)] : [path];
  });
}
function change(path, value, remove = false) {
  const clone = structuredClone(config);
  let parent = clone;
  for (const key of path.slice(0, -1)) parent = parent[key];
  if (remove) delete parent[path.at(-1)];
  else parent[path.at(-1)] = value;
  return clone;
}
test("complete current native settings are accepted unchanged without defaults or coercion", () => {
  const candidate = structuredClone(config);
  validateAndroidSettings(candidate);
  assert.deepEqual(candidate, config);
});
test("every root and nested setting is required and refuses null", () => {
  for (const path of paths(config)) {
    assert.throws(() => validateAndroidSettings(change(path, undefined, true)), Error, path.join("."));
    assert.throws(() => validateAndroidSettings(change(path, null)), Error, path.join("."));
  }
});
test("unknown root UI palette and lyric fields cannot enter generated configuration", () => {
  for (const path of [["unknown"], ["lyricUnknown"], ["ui", "unknown"], ["ui", "colors", "unknown"]]) {
    assert.throws(() => validateAndroidSettings(change(path, true)), /fixture settings/);
  }
});
test("all current positive integer knobs reject nonfinite fractional coerced and platform-overflow values", () => {
  for (const path of paths(config)) {
    let current = config;
    for (const key of path) current = current[key];
    if (typeof current !== "number" || path[0] === "listCompressionLevel") continue;
    for (const value of [0, -1, 0.5, NaN, Infinity, 2147483648, String(current)]) {
      assert.throws(() => validateAndroidSettings(change(path, value)), Error, path.join("."));
    }
  }
  for (const value of [0, 9]) validateAndroidSettings(change(["listCompressionLevel"], value));
  for (const value of [-1, 10, 0.5, NaN, "6"]) {
    assert.throws(() => validateAndroidSettings(change(["listCompressionLevel"], value)), /transport/);
  }
});
test("native byte count and timeout ceilings retain exact inclusive platform bounds", () => {
  for (const key of Object.keys(config).filter(key => key.startsWith("lyric") && key !== "lyricDescriptorUrl")
    .concat(["connectTimeoutMs", "readTimeoutMs"])) {
    for (const value of [1, 2147483647]) validateAndroidSettings(change([key], value));
    assert.throws(() => validateAndroidSettings(change([key], 2147483648)), /fixture settings/);
  }
});
test("native endpoints refuse credential normalization controls and empty query/fragment ambiguity", () => {
  for (const key of ["descriptorUrl", "lyricDescriptorUrl", "canonicalBaseUrl"]) {
    const suffix = key === "canonicalBaseUrl" ? "/" : "/lyrics/latest.json";
    for (const host of ["https://user:synthetic@example.test", "https://EXAMPLE.test",
      "https://example.test:443", "https://example.test\\unsafe"]) {
      assert.throws(() => validateAndroidSettings(change([key], host + suffix)), Error);
    }
    for (const tail of ["?", "#", "?x=1", "#x", "\n", "\r"]) {
      assert.throws(() => validateAndroidSettings(change([key], "https://example.test" + suffix + tail)), Error);
    }
    validateAndroidSettings(change([key], "https://example.test" + suffix));
  }
  validateAndroidSettings(change(["descriptorUrl"], "http://10.0.2.2:5173/demo/latest.json"));
  assert.throws(() => validateAndroidSettings(change(["lyricDescriptorUrl"], "http://example.test/lyrics/latest.json")), /endpoint/);
});
test("SDK version order and native reader defaults steps and density relationships remain explicit", () => {
  for (const [path, value] of [
    [["targetSdk"], 25], [["compileSdk"], 35], [["ui", "readerFontMinSp"], 30],
    [["ui", "readerFontMaxSp"], 20], [["ui", "readerFontStepSp"], 3],
    [["ui", "readerLineHeightSp"], 27], [["ui", "densityNormalPercent"], 101],
    [["ui", "densityCompactPercent"], 100], [["ui", "densityRelaxedPercent"], 100],
  ]) assert.throws(() => validateAndroidSettings(change(path, value)), Error);
});
