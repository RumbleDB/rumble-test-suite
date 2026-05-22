#!/usr/bin/env python3
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import questionary

REPO_ROOT = Path(__file__).resolve().parent.parent
TEST_DIR = REPO_ROOT / "src" / "test" / "java" / "iq"
TEST_CLASS_PATTERN = re.compile(r"public\s+class\s+([A-Za-z0-9_]+)")
GET_DATA_PATTERN = re.compile(r'return\s+getData\("([^"]+)"(?:,\s*(true|false))?\);')
ParserName = Literal["all", "jsoniq", "xquery"]
USAGE = """Usage:
  python3 scripts/run_selected_tests.py
  python3 scripts/run_selected_tests.py -- [extra Maven args]
"""


@dataclass(frozen=True)
class TestClass:
    class_name: str
    parser: Literal["jsoniq", "xquery"]
    suite: str


def discover_tests() -> list[TestClass]:
    tests: list[TestClass] = []
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
        tests.append(TestClass(class_name=class_name, parser=parser, suite=suite))

    return tests


def filter_tests(
    tests: list[TestClass], query: str, parser_filter: ParserName
) -> list[TestClass]:
    filtered = tests
    if parser_filter != "all":
        filtered = [test for test in filtered if test.parser == parser_filter]

    if query:
        needle = query.lower()
        filtered = [
            test
            for test in filtered
            if needle in test.class_name.lower() or needle in test.suite.lower()
        ]

    return filtered


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


def build_choices(tests: list[TestClass]):
    return [
        questionary.Choice(
            title=f"{test.class_name}  [{test.parser}]  {test.suite}",
            value=test.class_name,
            checked=True,
        )
        for test in tests
    ]


def run_maven(class_names: list[str], maven_args: list[str]) -> int:
    command = ["mvn", f"-Dtest={','.join(class_names)}", "test", *maven_args]
    print(f"Running: {shlex.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    return result.returncode


def run_interactive(tests: list[TestClass], maven_args: list[str]) -> int:
    while True:
        current_parser = questionary.select(
            "Choose parser",
            choices=["all", "jsoniq", "xquery"],
            default="all",
        ).ask()
        if current_parser is None:
            return 0

        query = questionary.text(
            "Filter by class name or suite",
            instruction="Leave empty to show all tests.",
        ).ask()
        if query is None:
            return 0

        visible = filter_tests(tests, query, current_parser)
        if not visible:
            retry = questionary.confirm(
                "No matching tests. Change filters?", default=True
            ).ask()
            if retry:
                continue
            return 0

        chosen = questionary.checkbox(
            f"Select test classes to run ({len(visible)} matches)",
            choices=build_choices(visible),
            instruction="Use space to toggle and enter to confirm.",
            validate=lambda selected: (
                True if selected else "Select at least one test class."
            ),
        ).ask()
        if chosen is None:
            continue

        if not questionary.confirm(
            f"Run {len(chosen)} selected test class(es)?",
            default=True,
        ).ask():
            continue

        return run_maven(chosen, maven_args)


def main() -> int:
    tests = discover_tests()
    maven_args = parse_maven_args(sys.argv[1:])

    if not tests:
        print(f"No test classes found in {TEST_DIR}", file=sys.stderr)
        return 1

    return run_interactive(tests, maven_args)


if __name__ == "__main__":
    raise SystemExit(main())
