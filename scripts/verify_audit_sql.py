"""Check an isolated review run's durable GriefLogger rows against its native writer observations."""

import argparse
import json
from pathlib import Path
import sqlite3


def verify(run: Path) -> dict:
    report = json.loads((run / "review-verification.json").read_text(encoding="utf-8"))
    cases = report["native_grieflogger"]
    if not isinstance(cases, list) or not cases:
        raise ValueError("This run did not execute native GriefLogger cases")
    checks = []
    with sqlite3.connect(f"file:{(run / 'database.db').resolve().as_posix()}?mode=ro", uri=True) as db:
        for case in cases:
            xyz = (case["x"], case["y"], case["z"])
            breaks = db.execute("SELECT count(*) FROM blocks WHERE x=? AND y=? AND z=? AND action=0", xyz).fetchone()[0]
            interactions = db.execute("SELECT count(*) FROM blocks WHERE x=? AND y=? AND z=? AND action=2", xyz).fetchone()[0]
            containers, amount = db.execute("SELECT count(*),coalesce(sum(amount),0) FROM containers WHERE x=? AND y=? AND z=?", xyz).fetchone()
            if (breaks, containers, amount) != (case["block_writes"], case["container_writes"], case["snapshot_items"]):
                raise AssertionError((case["scenario"], breaks, containers, amount))
            # A right-click attempt may add its own INTERACT record; the earlier seeded history must survive.
            if case["scenario"] in ("no-removal", "rotation-only", "virtual-missing-source") and interactions < 1:
                raise AssertionError((case["scenario"], "interaction history was deleted"))
            if case["scenario"] in ("removed", "exception-after-removal") and interactions != 0:
                raise AssertionError((case["scenario"], "native interaction cleanup was lost"))
            checks.append(dict(scenario=case["scenario"], break_rows=breaks, interaction_rows=interactions,
                               container_rows=containers, container_amount=amount))
    result = dict(status="PASS", neoforge=report["neoforge"], mods=report["mods"], cases=len(checks), checks=checks)
    with (run / "sql-audit-verification.json").open("w", encoding="utf-8", newline="\n") as output:
        output.write(json.dumps(result, indent=2) + "\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_directory", type=Path)
    result = verify(parser.parse_args().run_directory)
    print(json.dumps({key: result[key] for key in ("status", "neoforge", "cases", "mods")}))
