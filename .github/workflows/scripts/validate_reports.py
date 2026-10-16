"""Reject incomplete QT3 reports before publishing a conformance dashboard."""

import argparse
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET


def validate_reports(directory, parser=None):
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"No Surefire XML reports found in {directory}")
    for report in reports:
        root = ET.parse(report).getroot()
        cases = root.findall("testcase")
        if root.tag != "testsuite" or not cases or int(root.get("tests", "0")) != len(cases):
            raise ValueError(f"Empty or incomplete test suite in {report}")
        for case in cases:
            if not re.fullmatch(r"test\[[^/\s]+/.+\.xml:.+\]", case.get("name", "")):
                raise ValueError(f"Expected a QT3 case, found {case.get('name')!r} in {report}; check suite initialization")
        if parser is not None:
            property_node = root.find("properties/property[@name='parser']")
            if property_node is None or property_node.get("value") != parser:
                raise ValueError(f"Expected parser {parser!r} in {report}")
    return reports


def main():
    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("directory", type=Path)
    arguments.add_argument("--parser", choices=("jsoniq", "xquery"), required=True)
    arguments.add_argument("--merge-to", type=Path, help="Validate all matrix artifacts before merging reports")
    args = arguments.parse_args()
    try:
        if args.merge_to is None:
            reports = validate_reports(args.directory, args.parser)
        else:
            # Each top-level iq test class is a required matrix job. Discovering the classes
            # here also catches a suite accidentally removed from the workflow matrix.
            repository = Path(__file__).resolve().parents[3]
            suites = sorted((repository / "src/test/java/iq").glob("*Test.java"))
            if not suites:
                raise ValueError("No QT3 suite classes found")
            reports = []
            for suite in suites:
                artifact = args.directory / f"surefire-{args.parser}-{suite.stem}"
                reports.extend(validate_reports(artifact, args.parser))
            # Preserve artifact boundaries until every matrix job has been accounted for.
            args.merge_to.mkdir(parents=True, exist_ok=True)
            for report in reports:
                shutil.copyfile(report, args.merge_to / report.name)
        print(f"Validated {len(reports)} Surefire reports for {args.parser}")
    except (ValueError, ET.ParseError, OSError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
