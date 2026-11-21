#!/usr/bin/env node
/**
 * Aquila Bank Repository Hygiene & Specification Guard
 *
 * Verifies:
 * 1. Presence of required repository governance and release configuration files.
 * 2. Zero local agent configuration files or legacy PR/request templates tracked in Git.
 * 3. Zero tool attribution watermarks in tracked text files (with explicit byte ceiling).
 */

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const ROOT = process.cwd();
const MAX_SCAN_BYTES = 2 * 1024 * 1024;

const REQUIRED_TRACKED_FILES = [
  "README.md",
  "LICENSE",
  "NOTICE",
  "CONTRIBUTING.md",
  "SECURITY.md",
  "THIRD_PARTY_NOTICES.md",
  "TODO.md",
  ".github/ISSUE_TEMPLATE/bug_report.yml",
  ".github/ISSUE_TEMPLATE/config.yml",
  ".github/release.yml",
];

const FORBIDDEN_EXACT_PATHS = new Set([
  "AGENTS.md",
  "CLAUDE.md",
  "CLAUDE.local.md",
  "GEMINI.md",
  "CURSOR.md",
  "COPILOT.md",
  "docs/policy-decisions.md",
  ".coderabbit.yaml",
  ".github/dependabot.yml",
  ".github/pull_request_template.md",
  ".github/ISSUE_TEMPLATE/feature_request.yml",
  ".github/ISSUE_TEMPLATE/performance_request.yml",
  ".github/ISSUE_TEMPLATE/task_request.yml",
]);

const FORBIDDEN_PREFIXES = [
  ".agents/",
  ".claude/",
  ".gemini/",
  ".codex/",
  ".cursor/",
  ".aider/",
  ".windsurf/",
  ".ai/",
];

const WATERMARK_ALLOWLIST = new Set([
  ".githooks/commit-msg",
  "tools/ci/check-repo-hygiene.mjs",
]);

const BINARY_EXTENSIONS = new Set([
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".webp",
  ".ico",
  ".woff",
  ".woff2",
  ".ttf",
  ".otf",
  ".jar",
  ".zip",
  ".gz",
  ".pdf",
]);

const ATTRIBUTION_PATTERN =
  /\bCo-Authored-By:\s*(?:Claude|Cursor|Gemini|Codex|ChatGPT)\b|Generated with \[?(?:Claude|Gemini|Cursor)|Made[- ]with:?[ \t]*Cursor|cursoragent@cursor\.com|noreply@anthropic\.com/i;

function readTrackedPaths() {
  const raw = execFileSync("git", ["ls-files", "-z"], {
    cwd: ROOT,
    maxBuffer: 16 * 1024 * 1024,
  });
  const paths = [];
  let start = 0;
  for (let i = 0; i < raw.length; i += 1) {
    if (raw[i] === 0) {
      if (i > start) {
        paths.push(raw.toString("utf8", start, i));
      }
      start = i + 1;
    }
  }
  return paths;
}

function hasForbiddenPrefix(relPath) {
  for (let i = 0; i < FORBIDDEN_PREFIXES.length; i += 1) {
    if (relPath.startsWith(FORBIDDEN_PREFIXES[i])) {
      return true;
    }
  }
  return false;
}

function main() {
  const trackedPaths = readTrackedPaths();
  const trackedSet = new Set(trackedPaths);
  const violations = [];

  for (let i = 0; i < REQUIRED_TRACKED_FILES.length; i += 1) {
    const req = REQUIRED_TRACKED_FILES[i];
    if (!trackedSet.has(req) || !fs.existsSync(path.join(ROOT, req))) {
      violations.push(`missing required tracked file: ${req}`);
    }
  }

  for (let i = 0; i < trackedPaths.length; i += 1) {
    const relPath = trackedPaths[i];
    if (FORBIDDEN_EXACT_PATHS.has(relPath) || hasForbiddenPrefix(relPath)) {
      violations.push(`forbidden tracked path: ${relPath}`);
      continue;
    }

    if (WATERMARK_ALLOWLIST.has(relPath)) {
      continue;
    }

    const ext = path.extname(relPath).toLowerCase();
    if (BINARY_EXTENSIONS.has(ext)) {
      continue;
    }

    const absPath = path.join(ROOT, relPath);
    let stat;
    try {
      stat = fs.statSync(absPath);
    } catch {
      continue;
    }
    if (!stat.isFile() || stat.size === 0) {
      continue;
    }
    if (stat.size > MAX_SCAN_BYTES) {
      violations.push(
        `tracked text candidate exceeds ${MAX_SCAN_BYTES} bytes scan budget: ${relPath} (${stat.size} bytes)`,
      );
      continue;
    }

    const content = fs.readFileSync(absPath, "utf8");
    const match = ATTRIBUTION_PATTERN.exec(content);
    if (match) {
      violations.push(`prohibited attribution trace in ${relPath}: "${match[0]}"`);
    }
  }

  if (violations.length > 0) {
    console.error(`[repo-hygiene] failed with ${violations.length} violation(s):`);
    for (let i = 0; i < violations.length; i += 1) {
      console.error(`  - ${violations[i]}`);
    }
    process.exit(1);
  }

  console.log(`[repo-hygiene] ok (${trackedPaths.length} tracked files checked)`);
}

main();
