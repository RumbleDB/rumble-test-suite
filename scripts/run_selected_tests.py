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
    suite: str
    class_name: str


def discover_targets() -> list[TestTarget]:
    targets: list[TestTarget] = []
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

        targets.append(
            TestTarget(
                suite=data_match.group(1),
                class_name=class_match.group(1),
            )
        )

    return targets


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


def run_test(class_name: str, parser: ParserName, maven_args: list[str]) -> int:
    command = [
        "mvn",
        f"-Dtest={class_name}",
        f"-Dparser={parser}",
        "test",
        *maven_args,
    ]
    print(f"Running: {shlex.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    return result.returncode


def run_analysis(baseline: str | None = None) -> int:
    command = ["mvn", "exec:java@analytics"]
    if baseline:
        command.append(f"-Dbaseline={baseline}")
    print(f"Running: {shlex.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    return result.returncode


def run_pipeline(
    selected_tests: list[tuple[str, ParserName]], maven_args: list[str]
) -> int:
    clear_surefire_reports()

    exit_code = 0
    for class_name, parser in selected_tests:
        test_exit_code = run_test(class_name, parser, maven_args)

        if exit_code == 0 and test_exit_code != 0:
            exit_code = test_exit_code

    analysis_exit_code = run_analysis()
    if exit_code == 0 and analysis_exit_code != 0:
        exit_code = analysis_exit_code

    return exit_code


def run_interactive(targets: list[TestTarget], maven_args: list[str]) -> int:
    while True:
        chosen = questionary.checkbox(
            f"Select test classes to run ({len(targets)} available)",
            choices=[
                questionary.Choice(
                    title=f"{target.class_name}  {target.suite}",
                    value=target.class_name,
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
            f"Run {len(chosen)} selected test case(s) with {', '.join(parsers)} and generate one analysis report?",
            default=True,
        ).ask():
            continue

        selected_tests = [
            (class_name, parser) for class_name in chosen for parser in parsers
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
