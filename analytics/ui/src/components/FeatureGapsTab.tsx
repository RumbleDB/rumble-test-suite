import { For, Show, createMemo, createSignal } from "solid-js";
import { Search, AlertCircle, Play } from "./Icons";
import { HighlightText } from "./HighlightText";
import {
  SKIP_CATEGORY_META,
  SKIP_CATEGORY_ORDER,
  formatDependency,
  getSingleTestCaseCommand,
  suiteOfCase,
} from "../lib/analysis";
import type { DependencyRow, SkipCategory, ViewModel } from "../lib/analysis";

type CategoryFilter = SkipCategory | "ALL";
type SortMode = "count" | "exclusive" | "name";

type FeatureGapsTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  setActiveSuite: (suite: string) => void;
  category: CategoryFilter;
  setCategory: (category: CategoryFilter) => void;
  expandedKey: string | null;
  setExpandedKey: (key: string | null) => void;
  copiedKey: string | null;
  handleCopyCommand: (command: string, key: string) => void;
};

type ScopedRow = DependencyRow & { scopedCases: DependencyRow["cases"] };

/** Splits a dependency's skipped tests into what implementing it would achieve. */
export function gapSegments(row: Pick<DependencyRow, "count" | "actionable" | "exclusive" | "category">) {
  if (row.category !== "missing-feature") {
    return [{ label: "Skipped", count: row.count, color: SKIP_CATEGORY_META[row.category].color, opacity: 1 }];
  }
  return [
    { label: "Sole blocker", count: row.exclusive, color: "var(--skip-missing)", opacity: 1 },
    { label: "Also needs other missing features", count: row.actionable - row.exclusive, color: "var(--skip-missing-partial)", opacity: 1 },
    { label: "Ruled out by another dependency", count: row.count - row.actionable, color: "var(--skip-na)", opacity: 1 },
  ];
}

export function GapBar(props: { row: Pick<DependencyRow, "count" | "actionable" | "exclusive" | "category">; max: number }) {
  return (
    <div class="gap-bar" style={{ width: `${Math.max((props.row.count / Math.max(props.max, 1)) * 100, 2)}%` }}>
      <For each={gapSegments(props.row).filter((segment) => segment.count > 0)}>
        {(segment) => (
          <div
            style={{ flex: `${segment.count} 0 0`, background: segment.color, opacity: segment.opacity }}
            title={`${segment.label}: ${segment.count}`}
          />
        )}
      </For>
    </div>
  );
}

export function FeatureGapsTab(props: FeatureGapsTabProps) {
  const [search, setSearch] = createSignal("");
  const [sortBy, setSortBy] = createSignal<SortMode>("count");

  const suiteOptions = createMemo(() => ["ALL", ...props.viewModel.suites.map((suite) => suite.name)]);
  const parserOf = (suiteName: string) =>
    props.viewModel.suites.find((suite) => suite.name === suiteName)?.parser || "jsoniq";

  // Restrict every dependency to the selected suite and recompute its counts from its cases.
  const scopedRows = createMemo<ScopedRow[]>(() =>
    props.viewModel.dependencyRows
      .map((row) => {
        if (props.activeSuite === "ALL") {
          return { ...row, scopedCases: row.cases };
        }
        const scopedCases = row.cases.filter((c) => suiteOfCase(c.id) === props.activeSuite);
        return {
          ...row,
          scopedCases,
          count: scopedCases.length,
          actionable: scopedCases.filter((c) => c.skip?.category === "missing-feature").length,
          exclusive: scopedCases.filter((c) => c.skip?.dependencies.length === 1).length,
        };
      })
      .filter((row) => row.count > 0)
  );

  const categoryOptions = createMemo(() =>
    SKIP_CATEGORY_ORDER.filter(
      (category) => category !== "unclassified" || scopedRows().some((row) => row.category === category)
    )
  );

  const visibleRows = createMemo(() => {
    const query = search().trim().toLowerCase();
    const rows = scopedRows().filter((row) => {
      if (props.category !== "ALL" && row.category !== props.category) {
        return false;
      }
      if (!query) {
        return true;
      }
      return [row.label, row.key, ...row.scopedCases.map((c) => c.id)].join("\n").toLowerCase().includes(query);
    });
    return [...rows].sort((left, right) => {
      if (sortBy() === "exclusive") {
        return right.exclusive - left.exclusive || right.count - left.count;
      }
      if (sortBy() === "name") {
        return left.label.localeCompare(right.label);
      }
      return right.count - left.count || left.label.localeCompare(right.label);
    });
  });

  const maxCount = createMemo(() => Math.max(...visibleRows().map((row) => row.count), 1));

  // Test-level counts: each skipped test is counted once, under its own category.
  const scopedSkipCounts = createMemo(() => {
    if (props.activeSuite === "ALL") {
      return props.viewModel.totals.skipCategories;
    }
    return props.viewModel.suites.find((suite) => suite.name === props.activeSuite)?.skipCategories
      ?? props.viewModel.totals.skipCategories;
  });

  const missingRows = createMemo(() => scopedRows().filter((row) => row.category === "missing-feature"));
  const soleBlockerTests = createMemo(() => missingRows().reduce((sum, row) => sum + row.exclusive, 0));

  return (
    <div class="tab-content-animate" style={{ display: "flex", "flex-direction": "column", gap: "20px" }}>
      <section class="stat-grid">
        <div class="panel stat-card stat-card-missing">
          <span class="stat-label">Blocked by Missing Features</span>
          <strong class="stat-value" style={{ color: "var(--skip-missing-ink)" }}>
            {scopedSkipCounts()["missing-feature"]}
          </strong>
          <span class="stat-hint">tests across {missingRows().length} features</span>
        </div>
        <div class="panel stat-card stat-card-missing">
          <span class="stat-label">Single Missing Feature</span>
          <strong class="stat-value" style={{ color: "var(--skip-missing-ink)" }}>{soleBlockerTests()}</strong>
          <span class="stat-hint">tests that one feature would unlock</span>
        </div>
        <div class="panel stat-card stat-card-na">
          <span class="stat-label">Not Applicable</span>
          <strong class="stat-value" style={{ color: "var(--muted)" }}>{scopedSkipCounts()["not-applicable"]}</strong>
          <span class="stat-hint">negative tests &amp; alternative behaviour</span>
        </div>
        <div class="panel stat-card stat-card-na">
          <span class="stat-label">Other Spec</span>
          <strong class="stat-value" style={{ color: "var(--muted)" }}>{scopedSkipCounts()["other-spec"]}</strong>
          <span class="stat-hint">XPath-only or older XQuery tests</span>
        </div>
      </section>

      <section class="panel" style={{ display: "flex", "flex-direction": "column", gap: "16px" }}>
        <div class="section-header" style={{ "flex-wrap": "wrap", gap: "12px" }}>
          <div>
            <h2>Unmet Dependencies</h2>
            <p class="section-subtitle">
              Every QT3 dependency that keeps a test from running. A test with several unmet dependencies is listed under each.
            </p>
          </div>
          <div class="segmented" role="tablist">
            <For each={[...categoryOptions(), "ALL" as const]}>
              {(category) => (
                <button
                  classList={{ "segmented-active": props.category === category }}
                  onClick={() => props.setCategory(category)}
                  title={category === "ALL" ? "All skip reasons" : SKIP_CATEGORY_META[category].description}
                >
                  {category === "ALL" ? "All" : SKIP_CATEGORY_META[category].label}
                  <span class="segmented-count">
                    {category === "ALL"
                      ? scopedRows().length
                      : scopedRows().filter((row) => row.category === category).length}
                  </span>
                </button>
              )}
            </For>
          </div>
        </div>

        <div class="toolbar" style={{ margin: 0 }}>
          <div class="search-input-wrapper">
            <Search size={14} />
            <input
              type="text"
              class="search-input"
              placeholder="Search features, test cases..."
              value={search()}
              onInput={(e) => setSearch(e.currentTarget.value)}
            />
          </div>
          <select class="select-filter" value={props.activeSuite} onChange={(e) => props.setActiveSuite(e.currentTarget.value)}>
            <For each={suiteOptions()}>
              {(option) => <option value={option}>{option === "ALL" ? "All Suites" : option}</option>}
            </For>
          </select>
          <select class="select-filter" value={sortBy()} onChange={(e) => setSortBy(e.currentTarget.value as SortMode)}>
            <option value="count">Sort: Skipped tests</option>
            <option value="exclusive">Sort: Sole blocker (quick wins)</option>
            <option value="name">Sort: Name</option>
          </select>
        </div>

        <Show when={props.category === "missing-feature" || props.category === "ALL"}>
          <div class="gap-legend">
            <For each={gapSegments({ count: 0, actionable: 0, exclusive: 0, category: "missing-feature" })}>
              {(segment) => (
                <span>
                  <span class="legend-dot" style={{ background: segment.color, opacity: segment.opacity }} />
                  {segment.label}
                </span>
              )}
            </For>
          </div>
        </Show>

        <Show
          when={visibleRows().length > 0}
          fallback={
            <div class="empty-state" style={{ padding: "32px" }}>
              <AlertCircle size={32} />
              <h3>No skipped tests match</h3>
              <p>Try another category, suite, or search.</p>
            </div>
          }
        >
          <div class="table-container">
            <table class="dev-table">
              <thead>
                <tr>
                  <th style={{ width: "28%" }}>Dependency</th>
                  <th style={{ width: "12%" }}>Reason</th>
                  <th style={{ "text-align": "right", width: "8%" }} title="Skipped tests with this unmet dependency">Tests</th>
                  <th style={{ "text-align": "right", width: "10%" }} title="Tests that would run if this dependency alone were satisfied">Sole blocker</th>
                  <th style={{ width: "26%" }}>Breakdown</th>
                  <th>Suites</th>
                </tr>
              </thead>
              <tbody>
                <For each={visibleRows()}>
                  {(row) => {
                    const expanded = () => props.expandedKey === row.key;
                    return (
                      <>
                        <tr
                          class="table-row"
                          classList={{ "table-row-active": expanded() }}
                          onClick={() => props.setExpandedKey(expanded() ? null : row.key)}
                        >
                          <td style={{ "font-family": "var(--font-mono)", "font-size": "0.8rem", "font-weight": "600" }}>
                            <span style={{ color: "var(--muted)", "margin-right": "6px", "font-size": "0.68rem" }}>{expanded() ? "▼" : "▶"}</span>
                            <HighlightText text={row.label} query={search()} />
                          </td>
                          <td>
                            <span class={`pill pill-skip-${row.category}`} title={SKIP_CATEGORY_META[row.category].description}>
                              {SKIP_CATEGORY_META[row.category].label}
                            </span>
                          </td>
                          <td style={{ "text-align": "right", "font-family": "var(--font-mono)", "font-weight": "700" }}>{row.count}</td>
                          <td style={{ "text-align": "right", "font-family": "var(--font-mono)", color: row.exclusive > 0 && row.category === "missing-feature" ? "var(--skip-missing-ink)" : "var(--muted)" }}>
                            {row.category === "missing-feature" ? row.exclusive : "–"}
                          </td>
                          <td>
                            <GapBar row={row} max={maxCount()} />
                          </td>
                          <td style={{ "font-size": "0.76rem", color: "var(--muted)" }}>
                            {row.suites
                              .filter((suite) => props.activeSuite === "ALL" || suite.name === props.activeSuite)
                              .map((suite) => `${suite.name} ${suite.count}`)
                              .join(" · ")}
                          </td>
                        </tr>
                        <Show when={expanded()}>
                          <tr class="gap-row-detail">
                            <td colSpan={6}>
                              <p style={{ "font-size": "0.78rem", color: "var(--muted)", "margin-bottom": "10px" }}>
                                {SKIP_CATEGORY_META[row.category].description}.
                                <Show when={row.category === "missing-feature"}>
                                  {" "}Implementing it lets {row.exclusive} of {row.count} tests run; {row.actionable - row.exclusive} also need another missing feature
                                  {row.count > row.actionable ? `, and ${row.count - row.actionable} are ruled out by another dependency` : ""}.
                                </Show>
                              </p>
                              <div class="gap-case-list">
                                <For each={row.scopedCases.filter((c) => !search().trim() || c.id.toLowerCase().includes(search().trim().toLowerCase()) || row.label.toLowerCase().includes(search().trim().toLowerCase()))}>
                                  {(c) => {
                                    const suiteName = suiteOfCase(c.id);
                                    const command = () => getSingleTestCaseCommand(suiteName, c.id, parserOf(suiteName));
                                    return (
                                      <div class="gap-case">
                                        <span class="gap-case-id" title={c.description || c.id}>
                                          <HighlightText text={c.id} query={search()} />
                                        </span>
                                        <div class="gap-case-deps">
                                          <For each={c.skip?.dependencies || []}>
                                            {(dependency) => (
                                              <span
                                                class={`dependency-chip dependency-chip-${dependency.category}`}
                                                style={{ "font-weight": dependency.key === row.key ? "800" : "400" }}
                                                title={SKIP_CATEGORY_META[dependency.category]?.label}
                                              >
                                                {formatDependency(dependency)}
                                              </span>
                                            )}
                                          </For>
                                          <button
                                            class="affected-rerun-btn"
                                            onClick={(e) => {
                                              e.stopPropagation();
                                              props.handleCopyCommand(command(), c.id);
                                            }}
                                            title="Copy command to run just this test"
                                          >
                                            <Show when={props.copiedKey === c.id} fallback={<><Play size={10} style={{ "margin-right": "4px" }} /> Rerun</>}>
                                              Copied
                                            </Show>
                                          </button>
                                        </div>
                                      </div>
                                    );
                                  }}
                                </For>
                              </div>
                            </td>
                          </tr>
                        </Show>
                      </>
                    );
                  }}
                </For>
              </tbody>
            </table>
          </div>
        </Show>
      </section>
    </div>
  );
}
