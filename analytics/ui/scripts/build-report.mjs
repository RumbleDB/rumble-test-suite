import fs from "node:fs";
import path from "node:path";
import { build } from "vite";

const analysisJson = process.argv[2];

if (!analysisJson) {
    console.error("Usage: node scripts/build-report.mjs <analysis-json-path>");
    process.exit(1);
}

const rootDir = process.cwd();
const outDir = path.resolve(rootDir, "dist");
const indexPath = path.join(outDir, "index.html");
const analysisPath = path.resolve(rootDir, analysisJson);

await build();

let indexHtml = fs.readFileSync(indexPath, "utf8");

const cssMatch = indexHtml.match(/<link rel="stylesheet" crossorigin href="\.\/([^"]+\.css)">/);
if (!cssMatch) {
    throw new Error("Could not find built CSS asset in index.html");
}
const cssText = fs.readFileSync(path.join(outDir, cssMatch[1]), "utf8");
indexHtml = indexHtml.replace(cssMatch[0], () => `<style>${cssText}</style>`);

const jsMatch = indexHtml.match(/<script type="module" crossorigin src="\.\/([^"]+\.js)"><\/script>/);
if (!jsMatch) {
    throw new Error("Could not find built JS asset in index.html");
}
const jsText = fs.readFileSync(path.join(outDir, jsMatch[1]), "utf8");
indexHtml = indexHtml.replace(jsMatch[0], () => `<script type="module">${jsText}</script>`);

const analysisPayload = JSON.parse(fs.readFileSync(analysisPath, "utf8"));
const safeJson = JSON.stringify(analysisPayload).replace(/</g, "\\u003c");
const embeddedPayload = `<script id="initial-analysis-data" type="application/json">${safeJson}</script>`;

if (!indexHtml.includes("</body>")) {
    throw new Error("Expected </body> in built index.html");
}

indexHtml = indexHtml.replace("</body>", () => `  ${embeddedPayload}\n</body>`);
fs.writeFileSync(indexPath, indexHtml, "utf8");
fs.rmSync(path.join(outDir, "assets"), { recursive: true, force: true });
