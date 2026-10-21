import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { strictYaml } from "../shared/tools/generate-catalog.mjs";

const root = new URL("../", import.meta.url);
const read = path => readFile(new URL(path, root));
const hash = bytes => createHash("sha256").update(bytes).digest("hex");
const settings = strictYaml((await read("shared/settings/unicode.yml")).toString("utf8"));
const manifest = JSON.parse(await read("shared/generated/unicode/manifest.json"));
const bytes = await read("shared/generated/unicode/unicode16.json");
const data = JSON.parse(bytes);
for (const value of [settings, manifest, data]) {
  if (value.formatVersion !== 1 || value.profile !== "unicode16-default-full-nfc-v1" ||
      value.unicodeVersion !== "16.0.0") throw new Error("Unsupported native Unicode profile");
}
if (settings.normalization !== "NFC" || settings.caseFolding !== "default-full" ||
    JSON.stringify(settings.pipeline) !== '["NFC","default-full-casefold","NFC"]' ||
    manifest.data.path !== "unicode16.json" || manifest.vectors.path !== "../../fixtures/unicode-name-matching.json" ||
    manifest.license !== "../../resources/unicode16/LICENSE.txt" || data.license !== manifest.license)
  throw new Error("Unsupported native Unicode pipeline/resource paths");
for (const [resource, actual] of [
  [manifest.data, bytes],
  [manifest.vectors, await read("shared/fixtures/unicode-name-matching.json")],
]) {
  if (resource.bytes !== actual.length || resource.sha256 !== hash(actual))
    throw new Error("Unicode artifact hash/length mismatch");
}
if (JSON.stringify(data.sources) !== JSON.stringify(settings.sources))
  throw new Error("Unicode artifact provenance mismatch");
for (const [name, source] of Object.entries(settings.sources)) {
  if (hash(await read(`shared/resources/unicode16/${name}`)) !== source.sha256)
    throw new Error(`Unicode source hash mismatch: ${name}`);
}
console.log("Native Unicode16 profile, artifact/vector bytes and all six upstream identities verified.");
