#!/usr/bin/env python3

import argparse
import csv
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Summarize JaCoCo CSV coverage metrics."
    )
    parser.add_argument(
        "--csv-path",
        default="target/site/jacoco/jacoco.csv",
        help="Path to JaCoCo CSV report",
    )
    return parser.parse_args()


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return (covered / total * 100) if total else 100.0


def format_summary(csv_path: Path) -> str:
    im = ic = bm = bc = lm = lc = 0
    with csv_path.open() as f:
        reader = csv.DictReader(f)
        for row in reader:
            im += int(row["INSTRUCTION_MISSED"])
            ic += int(row["INSTRUCTION_COVERED"])
            bm += int(row["BRANCH_MISSED"])
            bc += int(row["BRANCH_COVERED"])
            lm += int(row["LINE_MISSED"])
            lc += int(row["LINE_COVERED"])

    return (
        "## Coverage Summary\n"
        f"- Instruction: {pct(ic, im):.2f}%\n"
        f"- Branch: {pct(bc, bm):.2f}%\n"
        f"- Line: {pct(lc, lm):.2f}%"
    )


def main() -> None:
    args = parse_args()
    csv_path = Path(args.csv_path)
    print(format_summary(csv_path))


if __name__ == "__main__":
    main()
