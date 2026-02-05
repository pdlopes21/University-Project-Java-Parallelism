#!/usr/bin/env python3
"""Generate a boxplot of total duration by algorithm and run statistical tests.

Outputs:
- boxplot_total_duration.png (in same folder)
- stats_results.txt (in same folder)
- descriptive_stats.csv (in same folder)

The script finds the top-level `Results.csv` by walking parent directories.
"""
from pathlib import Path
import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from scipy import stats
import itertools
import sys
import re


def find_results_csv(start_path: Path, name: str = "Results.csv") -> Path:
    p = start_path.resolve()
    for parent in [p] + list(p.parents):
        candidate = parent / name
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"{name} not found searching upwards from {start_path}")


def load_results(csv_path: Path) -> pd.DataFrame:
    df = pd.read_csv(csv_path, parse_dates=["timestamp"], skip_blank_lines=True)
    # Drop rows that are completely NaN
    df = df.dropna(how="all")
    # Ensure numeric columns are numeric
    numeric_cols = [c for c in df.columns if c.endswith("_ms")]
    for c in numeric_cols:
        df[c] = pd.to_numeric(df[c], errors="coerce")
    return df


def make_boxplot(df: pd.DataFrame, outpath: Path):
    # Convert milliseconds to seconds for readability
    df = df.copy()
    df["total_s"] = df["total_ms"] / 1000.0

    plt.figure(figsize=(8, 6))
    sns.set_theme(style="whitegrid")
    ax = sns.boxplot(x="algorithm", y="total_s", data=df, palette="Set2")
    ax.set_title("Total duration by algorithm (seconds)")
    ax.set_xlabel("Algorithm")
    ax.set_ylabel("Total time (s)")
    plt.tight_layout()
    plt.savefig(outpath, dpi=200)
    plt.close()


def make_executor_boxplot(df: pd.DataFrame, outpath: Path):
    # Expect `algorithm` column like 'ExecutorService 7 Threads'
    df = df.copy()
    # Try to extract thread count
    # If extraction fails, fall back to using the full algorithm string as category
    threads = df['algorithm'].astype(str).str.extract(r'(\d+)', expand=False)
    if threads.isnull().all():
        # fallback to algorithm categories
        df['category'] = df['algorithm'].astype(str)
        x_col = 'category'
        order = None
    else:
        df['threads'] = threads.astype(float).astype(int)
        # Use threads as categorical x in numeric order
        df['threads_cat'] = df['threads'].astype(str) + ' Threads'
        x_col = 'threads_cat'
        # Build an order by numeric threads
        order = [f"{t} Threads" for t in sorted(df['threads'].unique())]

    df['total_s'] = df['total_ms'] / 1000.0

    plt.figure(figsize=(10, 6))
    sns.set_theme(style="whitegrid")
    ax = sns.boxplot(x=x_col, y='total_s', data=df, order=order, palette='Set3')
    ax.set_title('ExecutorService total duration by thread count (seconds)')
    ax.set_xlabel('Threads')
    ax.set_ylabel('Total time (s)')
    plt.tight_layout()
    plt.savefig(outpath, dpi=200)
    plt.close()




def descriptive_stats(df: pd.DataFrame) -> pd.DataFrame:
    grouped = df.groupby("algorithm")["total_ms"].agg(["count", "mean", "median", "std", "min", "max"])
    grouped["mean_s"] = grouped["mean"] / 1000.0
    grouped["median_s"] = grouped["median"] / 1000.0
    return grouped


def run_stats(df: pd.DataFrame):
    # Prepare groups (in ms)
    groups = {name: group["total_ms"].dropna().values for name, group in df.groupby("algorithm")}


    # Pairwise Mann-Whitney U tests with Bonferroni correction
    pairs = list(itertools.combinations(groups.keys(), 2))
    pair_results = []
    m = len(pairs)
    for a, b in pairs:
        va = groups[a]
        vb = groups[b]
        if len(va) == 0 or len(vb) == 0:
            pair_results.append((a, b, None, None, None))
            continue
        # use two-sided test
        try:
            ures = stats.mannwhitneyu(va, vb, alternative='two-sided')
            p = ures.pvalue
            u = ures.statistic
        except TypeError:
            # older scipy uses 'pvalue' attribute named 'pvalue' as well but in case
            res = stats.mannwhitneyu(va, vb)
            u = res.statistic
            p = res.pvalue
        p_adj = min(p * m, 1.0)
        pair_results.append((a, b, u, p, p_adj))

    return pair_results


def save_stats_text(outpath: Path, pair_results, desc_df: pd.DataFrame):
    with open(outpath, "w", encoding="utf-8") as f:
        f.write("Descriptive statistics (total_ms):\n")
        f.write(desc_df.to_string())
        f.write("\n")
        f.write("\nPairwise Mann-Whitney U tests (two-sided) with Bonferroni correction:\n")
        f.write("pairs, U, raw_p, bonferroni_adj_p\n")
        for a, b, u, p, p_adj in pair_results:
            f.write(f"{a} vs {b}, {u}, {p}, {p_adj}\n")


def main():
    script_path = Path(__file__).resolve()
    try:
        results_csv = find_results_csv(script_path.parent)
    except FileNotFoundError as e:
        print(str(e), file=sys.stderr)
        sys.exit(2)

    print("Loading:", results_csv)
    df = load_results(results_csv)

    out_plot = script_path.parent / "boxplot_total_duration.png"
    out_stats = script_path.parent / "stats_results.txt"
    out_desc = script_path.parent / "descriptive_stats.csv"

    print("Making boxplot ->", out_plot)
    make_boxplot(df, out_plot)

    desc = descriptive_stats(df)
    desc.to_csv(out_desc)

    pair_results = run_stats(df)
    save_stats_text(out_stats, pair_results, desc)

    # Print a brief summary
    print("Summary:")
    print(desc)
    print('\nPairwise tests (Bonferroni-adjusted p):')
    for a, b, u, p, p_adj in pair_results:
        print(f"{a} vs {b}: U={u}, p={p} -> p_adj={p_adj}")

    # Also attempt to find and plot Executor_Results.csv if present
    try:
        executor_csv = find_results_csv(script_path.parent, name="Executor_Results.csv")
        print("Loading executor results:", executor_csv)
        edf = load_results(executor_csv)

        out_plot_exec = script_path.parent / "boxplot_executor_total_duration.png"
        out_stats_exec = script_path.parent / "stats_results_executor.txt"
        out_desc_exec = script_path.parent / "descriptive_stats_executor.csv"

        print("Making executor boxplot ->", out_plot_exec)
        make_executor_boxplot(edf, out_plot_exec)

        desc_exec = descriptive_stats(edf)
        desc_exec.to_csv(out_desc_exec)

        pair_results_exec = run_stats(edf)
        save_stats_text(out_stats_exec, pair_results_exec, desc_exec)

        print('\nExecutor summary:')
        print(desc_exec)
    except FileNotFoundError:
        print('No Executor_Results.csv found; skipping executor plots/stats')


if __name__ == '__main__':
    main()
