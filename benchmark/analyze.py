#!/usr/bin/env python3
"""
Analyze PosePerf logs collected by run_benchmark.sh.

The ONLY metric is frame-to-frame latency (`ds`): elapsed time between consecutive
result callbacks (and therefore consecutive displayed skeleton updates) — the gap
between the current frame and the latest/previous frame. Both apps display every
result, so this is the perceived responsiveness.

`ub_vis`/`motion` are read only to slice to frames where the upper body is visible
AND moving; ts is needed to derive `ds`. All other log fields are ignored.

Usage:
  python3 analyze.py --logs revamp.log rn.log \
      [--vis 0.8] [--min-vis 6] [--motion 0.005] [--csv out.csv]

Slice semantics (tunable offline; re-slicing never requires re-running on-device):
  --min-vis  : require ub_vis >= this many of the 8 upper-body landmarks visible
  --motion    : require upper-body summed displacement >= this (normalized units/frame)
"""

import argparse
import csv
import re
import statistics
from collections import defaultdict

LINE_RE = re.compile(
    r"ts=(\d+)\s+inf=([\d.]+)\s+backlog=(\d+)\s+ub_vis=(\d+)\s+motion=([\d.]+)"
)


def parse_file(path):
    """Return list of (app, round, dict) rows."""
    rows = []
    current_app = None
    current_round = None
    rounds = defaultdict(int)
    with open(path) as fh:
        for line in fh:
            if line.startswith("# round="):
                parts = dict(p.split("=", 1) for p in line[1:].strip().split())
                current_app = parts.get("app")
                current_round = rounds[current_app]
                rounds[current_app] += 1
                continue
            m = LINE_RE.search(line)
            if not m or current_app is None:
                continue
            rows.append(
                {
                    "app": current_app,
                    "round": current_round,
                    "ts": int(m.group(1)),
                    "ub_vis": int(m.group(4)),
                    "motion": float(m.group(5)),
                }
            )
    return rows


def add_cadence(rows):
    """Compute ds (ms between consecutive result callbacks) within each (app, round)."""
    by_group = defaultdict(list)
    for r in rows:
        by_group[(r["app"], r["round"])].append(r)
    for group in by_group.values():
        group.sort(key=lambda r: r["ts"])
        prev_ts = None
        for r in group:
            if prev_ts is None:
                r["ds"] = None
            else:
                r["ds"] = r["ts"] - prev_ts
            prev_ts = r["ts"]
    return rows


def pct(sorted_vals, p):
    if not sorted_vals:
        return None
    idx = min(len(sorted_vals) - 1, int(round(p / 100.0 * (len(sorted_vals) - 1))))
    return sorted_vals[idx]


def summarize(vals):
    if not vals:
        return {}
    s = sorted(vals)
    return {
        "n": len(vals),
        "mean": statistics.mean(s),
        "p50": pct(s, 50),
        "p90": pct(s, 90),
        "p95": pct(s, 95),
        "p99": pct(s, 99),
        "max": max(s),
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--logs", nargs="+", required=True, help="PosePerf log files")
    ap.add_argument("--min-vis", type=int, default=6, help="min upper-body visible landmarks")
    ap.add_argument("--motion", type=float, default=0.005, help="min upper-body motion/frame")
    ap.add_argument("--csv", help="optional CSV output path")
    args = ap.parse_args()

    all_rows = []
    for path in args.logs:
        all_rows.extend(parse_file(path))

    for r in all_rows:
        r["in_slice"] = r["ub_vis"] >= args.min_vis and r["motion"] >= args.motion

    add_cadence(all_rows)

    def vals(rows):
        return [r["ds"] for r in rows if r.get("ds") is not None]

    by_app = defaultdict(list)
    for r in all_rows:
        by_app[r["app"]].append(r)

    print(f"total frames parsed: {len(all_rows)}")
    print(f"Slice: ub_vis >= {args.min_vis} AND motion >= {args.motion}")
    print("=" * 70)
    results = {}
    for app in sorted(by_app):
        raw = by_app[app]
        sliced = [r for r in raw if r["in_slice"]]
        results[app] = vals(sliced)
        rounds = len({r["round"] for r in raw})

        print(f"[{app}] frames: total={len(raw)} in-slice={len(sliced)} rounds={rounds}")

        ca = summarize(vals(raw))
        cs = summarize(vals(sliced))
        if cs:
            print(
                f"  frame-to-frame latency (ms) [in-slice]  n={cs['n']} "
                f"mean={cs['mean']:.1f} p50={cs['p50']:.1f} p90={cs['p90']:.1f} "
                f"p95={cs['p95']:.1f} p99={cs['p99']:.1f} max={cs['max']:.1f} "
                f" (~{1000 / max(cs['mean'], 1e-9):.1f} displayed fps)"
            )
        else:
            print("  no frames in slice")
        if ca and ca.get("n"):
            print(
                f"  frame-to-frame latency (ms) [all]      n={ca['n']} mean={ca['mean']:.1f} "
                f"p50={ca['p50']:.1f} p90={ca['p90']:.1f} max={ca['max']:.1f}"
            )
        print()

    print("=" * 70)
    print("Frame-to-frame latency comparison (in-slice, ms) — LOWER = MORE RESPONSIVE")
    apps = sorted(results)
    print(f"{'metric':<10}" + "".join(f"{a:>14}" for a in apps))
    for metric in ("p50", "p90", "p95", "mean", "max"):
        row = f"{metric:<10}"
        for app in apps:
            s = summarize(results[app])
            row += f"{s.get(metric, float('nan'))!s:>14} "
        print(row)

    if len(apps) == 2:
        a, b = apps
        sa = summarize(results[a])
        sb = summarize(results[b])
        if sa.get("p50") is not None and sb.get("p50") is not None:
            if sa["p50"] == sb["p50"]:
                verdict = "tied"
            else:
                faster, slower = (a, b) if sa["p50"] < sb["p50"] else (b, a)
                gap_pct = abs(1 - sb["p50"] / sa["p50"]) * 100
                verdict = f"{faster} faster than {slower} by {gap_pct:.0f}%"
            print(
                f"\nRESULT ({a} vs {b}): in-slice frame-to-frame latency p50 "
                f"{sa['p50']:.1f}ms vs {sb['p50']:.1f}ms -> {verdict} (lower = better)"
            )

    if args.csv:
        with open(args.csv, "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["app", "round", "ts", "frame_to_frame_ms", "in_slice"])
            for r in all_rows:
                w.writerow([r["app"], r["round"], r["ts"],
                            r.get("ds", ""), int(r["in_slice"])])
        print(f"\nwrote {args.csv}")


if __name__ == "__main__":
    main()