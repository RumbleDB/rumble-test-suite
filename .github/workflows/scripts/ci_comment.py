#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

QT3TESTS_REPO_URL = "https://github.com/w3c/qt3tests/blob/master"


def load_analysis(analysis_json: str) -> dict:
    path = Path(analysis_json)
    if not path.is_file():
        raise FileNotFoundError(f"Could not find analysis file: {analysis_json}")
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def table_cell(value: object) -> str:
    return str(value).replace("\n", " ").replace("|", "\\|")


def parse_test_id(test_id: object) -> tuple[str, str]:
    text = str(test_id)
    if text.startswith("[") and "] " in text:
        file_name, case_name = text[1:].split("] ", 1)
        return file_name, case_name
    return "", text


def render_test_file_link(file_name: str) -> str:
    if not file_name:
        return ""
    return f"[{table_cell(file_name)}]({QT3TESTS_REPO_URL}/{file_name})"


def render_regression_details(analysis: dict) -> str:
    regressions = analysis.get("regressions", {})
    if not regressions:
        return "🎉 No regressions found for this build."

    total = sum(len(items) for items in regressions.values())
    lines = [
        f"‼️ Regression summary: **{total}** previously passing test(s) now fail, error, or skip.",
        "",
        "| Suite | Status | Test file | Test case | Message |",
        "| --- | --- | --- | --- | --- |",
    ]

    for suite, items in sorted(regressions.items()):
        for item in sorted(items, key=lambda entry: str(entry.get("id", ""))):
            raw_test_id = item.get("id", "")
            test_file, test_name = parse_test_id(raw_test_id)
            test_id = table_cell(test_name)
            test_file_link = render_test_file_link(test_file)
            status = table_cell(str(item.get("status", "")).upper())
            message = table_cell(item.get("message", ""))
            lines.append(
                f"| `{table_cell(suite)}` | `{status}` | {test_file_link} | `{test_id}` | `{message}` |"
            )

    return "\n".join(lines)


def render_improvement_details(analysis: dict) -> str:
    improvements = analysis.get("improvements", {})
    if not improvements:
        return ""

    total = sum(len(items) for items in improvements.values())
    lines = [
        f"✅ Improvement summary: **{total}** test(s) previously failing, errored, or skipped now pass.",
        "",
        "| Suite | Test file | Test case |",
        "| --- | --- | --- |",
    ]

    for suite, items in sorted(improvements.items()):
        for raw_test_id in sorted(items, key=str):
            test_file, test_name = parse_test_id(raw_test_id)
            test_file_link = render_test_file_link(test_file)
            lines.append(
                f"| `{table_cell(suite)}` | {test_file_link} | `{table_cell(test_name)}` |"
            )

    return "\n".join(lines)


def render_summary(summary: dict) -> str:
    lines = [
        "| Test Suite | Passing | Failing | Errors | Skipped | Total |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]

    totals = {"pass": 0, "fail": 0, "error": 0, "skip": 0}
    for suite, counts in sorted(summary.items()):
        passing = counts["pass"]
        failing = counts["fail"]
        errors = counts["error"]
        skipped = counts["skip"]
        total = passing + failing + errors + skipped
        lines.append(
            f"| `{table_cell(suite)}` | {passing} | {failing} | {errors} | {skipped} | {total} |"
        )
        totals["pass"] += passing
        totals["fail"] += failing
        totals["error"] += errors
        totals["skip"] += skipped

    total_tests = sum(totals.values())
    lines.append(
        f"| **Total** | **{totals['pass']}** | **{totals['fail']}** | "
        f"**{totals['error']}** | **{totals['skip']}** | **{total_tests}** |"
    )
    return "\n".join(lines)


def render_parser_section(parser_name: str, analysis: dict, artifacts_url: str) -> str:
    regression_details = render_regression_details(analysis)
    improvement_details = render_improvement_details(analysis)
    parts = []

    if regression_details:
        parts.extend([regression_details, ""])

    if improvement_details:
        parts.extend([improvement_details, ""])

    parts.extend(
        [
            "<details>",
            f"<summary>Summary of passed tests for {parser_name}</summary>",
            "",
            render_summary(analysis["summary"]),
            "",
            f"Full analysis report: see `analysis-{parser_name}` in [artifacts]({artifacts_url}).",
            "",
            "</details>",
        ]
    )

    return "\n".join(parts)


def build_comment(
    analysis_json: str,
    parser_name: str,
    run_id: str,
    baseline_run_id: str | None,
    repo_owner: str,
    repo_name: str,
) -> str:
    analysis = load_analysis(analysis_json)
    artifacts_url = (
        f"https://github.com/{repo_owner}/{repo_name}/actions/runs/{run_id}#artifacts"
    )

    parts = [f"## QT3 Test Results - Parser: `{parser_name}`"]
    if baseline_run_id:
        parts.extend(
            [
                f"Regression baseline: [run {baseline_run_id}]"
                f"(https://github.com/{repo_owner}/{repo_name}/actions/runs/{baseline_run_id})",
                "",
            ]
        )

    parts.append("")
    parts.append(render_parser_section(parser_name, analysis, artifacts_url))
    parts.append("")
    parts.append(f"[Download detailed test results]({artifacts_url})")
    return "\n".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--analysis-json",
        required=True,
        help="Path to the analysis.json file for this parser run",
    )
    parser.add_argument(
        "--parser",
        required=True,
        choices=["jsoniq", "xquery"],
        help="Parser used for this run",
    )
    parser.add_argument("--run-id", required=True, help="GitHub Actions run ID")
    parser.add_argument("--baseline-run-id", help="GitHub Actions baseline run ID")
    parser.add_argument("--repo-owner", required=True, help="Repository owner")
    parser.add_argument("--repo-name", required=True, help="Repository name")
    args = parser.parse_args()

    print(
        build_comment(
            args.analysis_json,
            args.parser,
            args.run_id,
            args.baseline_run_id,
            args.repo_owner,
            args.repo_name,
        )
    )


if __name__ == "__main__":
    main()
