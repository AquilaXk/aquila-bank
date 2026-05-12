import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const frontRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = join(frontRoot, "..");
const backendRoot = join(repoRoot, "back/src/main/java/com/aquilabank/global/web");
const matrixPath = join(frontRoot, "contracts/api-parity-matrix.json");

const httpMethods = {
  GetMapping: "GET",
  PostMapping: "POST",
  PutMapping: "PUT",
  DeleteMapping: "DELETE",
  PatchMapping: "PATCH",
};

function read(path) {
  return readFileSync(path, "utf8");
}

function listControllerFiles(root) {
  const items = [];
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const path = join(root, entry.name);
    if (entry.isDirectory()) {
      items.push(...listControllerFiles(path));
      continue;
    }
    if (entry.isFile() && entry.name.endsWith("Controller.java")) {
      items.push(path);
    }
  }
  return items.sort();
}

function annotationPath(annotation) {
  const match = annotation.match(/"([^"]*)"/);
  return match ? match[1] : "";
}

function joinPaths(basePath, endpointPath) {
  const base = basePath.endsWith("/") ? basePath.slice(0, -1) : basePath;
  if (!endpointPath) {
    return base || "/";
  }
  return `${base}${endpointPath.startsWith("/") ? endpointPath : `/${endpointPath}`}`;
}

function extractBackendEndpoints() {
  const endpoints = [];
  for (const filePath of listControllerFiles(backendRoot)) {
    const content = read(filePath);
    if (!content.includes("@RestController")) {
      continue;
    }

    const baseMatch = content.match(/@RequestMapping\(([^)]*)\)/);
    if (!baseMatch) {
      continue;
    }
    const basePath = annotationPath(baseMatch[1]);
    const mappingPattern =
      /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(?:\(([^)]*)\))?/g;

    for (const match of content.matchAll(mappingPattern)) {
      const [, annotationName, annotationBody = ""] = match;
      endpoints.push({
        method: httpMethods[annotationName],
        path: joinPaths(basePath, annotationPath(annotationBody)),
        source: relative(repoRoot, filePath),
      });
    }
  }

  return endpoints.sort((left, right) => {
    const leftKey = `${left.method} ${left.path}`;
    const rightKey = `${right.method} ${right.path}`;
    return leftKey.localeCompare(rightKey);
  });
}

function endpointKey(item) {
  return `${item.method} ${item.path}`;
}

function loadMatrix() {
  if (!existsSync(matrixPath)) {
    throw new Error(`missing API parity matrix: ${relative(repoRoot, matrixPath)}`);
  }
  return JSON.parse(read(matrixPath));
}

function assertEvidence(entry, failures) {
  if (entry.frontendStatus === "implemented") {
    if (!Array.isArray(entry.frontendEvidence) || entry.frontendEvidence.length === 0) {
      failures.push(`${entry.id}: implemented endpoint needs frontendEvidence`);
      return;
    }

    for (const evidence of entry.frontendEvidence) {
      const filePath = join(frontRoot, evidence.file);
      if (!existsSync(filePath)) {
        failures.push(`${entry.id}: missing evidence file ${evidence.file}`);
        continue;
      }
      const content = read(filePath);
      for (const expected of evidence.contains ?? []) {
        if (!content.includes(expected)) {
          failures.push(`${entry.id}: ${evidence.file} does not contain ${expected}`);
        }
      }
    }
    return;
  }

  if (entry.frontendStatus === "intentional-no-ui") {
    if (typeof entry.rationale !== "string" || entry.rationale.trim().length < 24) {
      failures.push(`${entry.id}: intentional-no-ui endpoint needs rationale`);
    }
    return;
  }

  failures.push(`${entry.id}: unsupported frontendStatus ${entry.frontendStatus}`);
}

function main() {
  const backendEndpoints = extractBackendEndpoints();
  const matrix = loadMatrix();
  const entries = matrix.endpoints ?? [];
  const failures = [];

  if (matrix.version !== 1) {
    failures.push("matrix.version must be 1");
  }

  const backendKeys = new Set(backendEndpoints.map(endpointKey));
  const matrixKeys = new Map();
  const ids = new Set();

  for (const entry of entries) {
    if (ids.has(entry.id)) {
      failures.push(`duplicate endpoint id ${entry.id}`);
    }
    ids.add(entry.id);

    const key = endpointKey(entry);
    if (matrixKeys.has(key)) {
      failures.push(`duplicate matrix endpoint ${key}`);
    }
    matrixKeys.set(key, entry);

    if (!backendKeys.has(key)) {
      failures.push(`matrix endpoint has no backend mapping: ${key}`);
    }
    assertEvidence(entry, failures);
  }

  for (const endpoint of backendEndpoints) {
    const key = endpointKey(endpoint);
    if (!matrixKeys.has(key)) {
      failures.push(`backend endpoint missing from matrix: ${key} (${endpoint.source})`);
    }
  }

  if (failures.length > 0) {
    console.error("[front-backend-api-parity] violations:");
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(1);
  }

  const implemented = entries.filter((entry) => entry.frontendStatus === "implemented").length;
  const intentionalNoUi = entries.filter(
    (entry) => entry.frontendStatus === "intentional-no-ui",
  ).length;
  console.log(
    `[front-backend-api-parity] passed: ${entries.length} endpoints, ${implemented} implemented, ${intentionalNoUi} intentional-no-ui`,
  );
}

main();
