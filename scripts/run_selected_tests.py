#!/usr/bin/env python3
import re
import shlex
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import questionary

REPO_ROOT = Path(__file__).resolve().parent.parent
TEST_DIR = REPO_ROOT / "src" / "test" / "java" / "iq"
SUREFIRE_REPORTS_DIR = REPO_ROOT / "target" / "surefire-reports"
TEST_CLASS_PATTERN = re.compile(r"public\s+class\s+([A-Za-z0-9_]+)")
GET_DATA_PATTERN = re.compile(r'return\s+getData\("([^"]+)"(?:,\s*(true|false))?\);')
ParserName = Literal["jsoniq", "xquery"]
USAGE = """Usage:
  python3 scripts/run_selected_tests.py
  python3 scripts/run_selected_tests.py -- [extra Maven test args]
"""


@dataclass(frozen=True)
class TestTarget:
    logical_name: str
    suite: str
    jsoniq_class: str | None
    xquery_class: str | None

    def class_name(self, parser: ParserName) -> str | None:
        return self.jsoniq_class if parser == "jsoniq" else self.xquery_class


def discover_targets() -> list[TestTarget]:
    grouped: dict[str, dict[str, str | None]] = {}
    for path in sorted(TEST_DIR.glob("*Test.java")):
        if path.name == "TestBase.java":
            continue

        content = path.read_text(encoding="utf-8")
        class_match = TEST_CLASS_PATTERN.search(content)
        data_match = GET_DATA_PATTERN.search(content)
        if class_match is None or data_match is None:
            print(
                f"Warning: Could not parse {path.name} for class name or suite.",
                file=sys.stderr,
            )
            continue

        class_name = class_match.group(1)
        suite = data_match.group(1)
        parser = "xquery" if data_match.group(2) == "true" else "jsoniq"
        logical_name = (
            class_name.removeprefix("XQuery") if parser == "xquery" else class_name
        )
        if logical_name not in grouped:
            grouped[logical_name] = {
                "suite": suite,
                "jsoniq_class": None,
                "xquery_class": None,
            }
        grouped[logical_name][f"{parser}_class"] = class_name

    return [
        TestTarget(
            logical_name=logical_name,
            suite=str(data["suite"]),
            jsoniq_class=data["jsoniq_class"],
            xquery_class=data["xquery_class"],
        )
        for logical_name, data in sorted(grouped.items())
    ]


def parse_maven_args(argv: list[str]) -> list[str]:
    if not argv:
        return []
    if argv[0] in {"-h", "--help"}:
        print(USAGE.strip())
        raise SystemExit(0)
    if argv[0] != "--":
        print("Pass extra Maven arguments after '--'.", file=sys.stderr)
        raise SystemExit(2)
    return argv[1:]


def clear_surefire_reports() -> None:
    if SUREFIRE_REPORTS_DIR.exists():
        shutil.rmtree(SUREFIRE_REPORTS_DIR)


def run_test(class_name: str, maven_args: list[str]) -> int:
    command = ["mvn", f"-Dtest={class_name}", "test", *maven_args]
    print(f"Running: {shlex.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    return result.returncode


def run_preaggregate(scope: str) -> int:
    command = ["mvn", "exec:java@analytics-pre", f"-Dexec.args={scope}"]
    print(f"Running: {shlex.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    return result.returncode


def run_pipeline(
    selected_tests: list[tuple[str, ParserName, str]], maven_args: list[str]
) -> int:
    clear_surefire_reports()

    exit_code = 0
    for class_name, parser, logical_name in selected_tests:
        test_exit_code = run_test(class_name, maven_args)
        preaggregate_exit_code = run_preaggregate(f"{parser}.{logical_name}")

        if exit_code == 0 and test_exit_code != 0:
            exit_code = test_exit_code
        if exit_code == 0 and preaggregate_exit_code != 0:
            exit_code = preaggregate_exit_code

    return exit_code


def run_interactive(targets: list[TestTarget], maven_args: list[str]) -> int:
    targets_by_name = {target.logical_name: target for target in targets}
    while True:
        chosen = questionary.checkbox(
            f"Select test classes to run ({len(targets)} available)",
            choices=[
                questionary.Choice(
                    title=f"{target.logical_name}  {target.suite}",
                    value=target.logical_name,
                )
                for target in targets
            ],
            instruction="Use space to toggle and enter to confirm.",
            validate=lambda selected: (
                True if selected else "Select at least one test class."
            ),
        ).ask()
        if chosen is None:
            return 0

        parsers = questionary.checkbox(
            "Choose parser(s)",
            choices=["jsoniq", "xquery"],
            instruction="Use space to toggle and enter to confirm.",
            validate=lambda selected: (
                True if selected else "Select at least one parser."
            ),
        ).ask()
        if parsers is None:
            return 0

        if not questionary.confirm(
            f"Run {len(chosen)} selected test case(s) with {', '.join(parsers)} and pre-aggregate each run?",
            default=True,
        ).ask():
            continue

        selected_tests = [
            (class_name, parser, logical_name)
            for logical_name in chosen
            for parser in parsers
            for class_name in [targets_by_name[logical_name].class_name(parser)]
            if class_name is not None
        ]
        return run_pipeline(selected_tests, maven_args)


def main() -> int:
    targets = discover_targets()
    maven_args = parse_maven_args(sys.argv[1:])

    if not targets:
        print(f"No test classes found in {TEST_DIR}", file=sys.stderr)
        return 1

    return run_interactive(targets, maven_args)


if __name__ == "__main__":
    raise SystemExit(main())
