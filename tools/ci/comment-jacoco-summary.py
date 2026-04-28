#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET


MARKER = "<!-- aquila-bank:jacoco-coverage-summary -->"


def coverage_bar(ratio):
    filled = min(10, int(ratio // 10))
    return "█" * filled + "░" * (10 - filled)


def read_counter(root, name):
    counter = next((item for item in root.findall("counter") if item.get("type") == name), None)
    if counter is None:
        return 0, 0, 100.0
    missed = int(counter.get("missed"))
    covered = int(counter.get("covered"))
    total = missed + covered
    ratio = 100.0 if total == 0 else covered * 100.0 / total
    return missed, covered, ratio


def count_baseline_exclusions(path):
    if path is None or not path.exists():
        return 0
    return sum(
        1
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    )


def format_counter(name, missed, covered, ratio):
    return f"| {name} | {covered} | {missed + covered} | {missed} | {ratio:.2f}% | `{coverage_bar(ratio)}` |"


def build_summary(report_path, baseline_path):
    root = ET.parse(report_path).getroot()
    line_missed, line_covered, line_ratio = read_counter(root, "LINE")
    status = "통과" if line_missed == 0 and line_ratio >= 100.0 else "미달"
    baseline_count = count_baseline_exclusions(baseline_path)
    sha = os.environ.get("GITHUB_SHA", "")
    sha_line = f"- Commit: `{sha}`" if sha else ""

    counters = [
        ("Instruction", *read_counter(root, "INSTRUCTION")),
        ("Line", *read_counter(root, "LINE")),
        ("Branch", *read_counter(root, "BRANCH")),
        ("Method", *read_counter(root, "METHOD")),
        ("Class", *read_counter(root, "CLASS")),
    ]

    lines = [
        MARKER,
        "## Jacoco 테스트 커버리지 요약",
        "",
        f"- 상태: **{status}**",
        f"- 전체 Line coverage: **{line_ratio:.2f}%**",
        "- 기준: full report line coverage 100%",
        f"- Baseline 제외 클래스: `{baseline_count}`개",
    ]
    if sha_line:
        lines.append(sha_line)
    lines.extend(
        [
            "",
            "| 유형 | 커버 | 전체 | 미커버 | 비율 | 그래프 |",
            "| --- | ---: | ---: | ---: | ---: | --- |",
        ]
    )
    lines.extend(format_counter(*item) for item in counters)
    lines.extend(
        [
            "",
            f"- XML: `{report_path}`",
            f"- Baseline: `{baseline_path}`",
        ]
    )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Create a Jacoco coverage summary PR comment body.")
    parser.add_argument("report", type=Path)
    parser.add_argument("baseline", type=Path)
    args = parser.parse_args()

    if not args.report.exists():
        raise SystemExit(f"Jacoco report not found: {args.report}")

    print(build_summary(args.report, args.baseline))


if __name__ == "__main__":
    main()
