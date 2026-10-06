export const STATUS_ORDER = ["PASS", "FAIL", "ERROR", "SKIP"] as const;

export type Status = (typeof STATUS_ORDER)[number];
/** Failure groups only cover tests that ran: passing tests are not in the report, and skips are feature gaps. */
export type IssueStatus = "FAIL" | "ERROR";
export type StatusFilter = IssueStatus | "ALL";
export type ParserMode = "jsoniq" | "xquery" | "default";

export const SKIP_CATEGORY_ORDER = ["missing-feature", "not-applicable", "other-spec", "unclassified"] as const;

export type SkipCategory = (typeof SKIP_CATEGORY_ORDER)[number];
export type SkipCategoryCounts = Record<SkipCategory, number>;

export const SKIP_CATEGORY_META: Record<SkipCategory, { label: string; description: string; color: string }> = {
  "missing-feature": {
    label: "Missing feature",
    description: "Requires a feature RumbleDB does not implement yet",
    color: "var(--skip-missing)",
  },
  "not-applicable": {
    label: "Not applicable",
    description: "Requires a supported feature to be absent, or an implementation-defined alternative",
    color: "var(--skip-na)",
  },
  "other-spec": {
    label: "Other spec",
    description: "Only applies to another language or specification version (e.g. XPath, XQuery 1.0)",
    color: "var(--skip-spec)",
  },
  unclassified: {
    label: "Unclassified",
    description: "Skip reason was not recorded in a structured form",
    color: "var(--skip)",
  },
};

export type SlowestCase = {
  id: string;
  time: number;
  status: string;
};

export type RawCountRecord = Partial<Record<Lowercase<Status>, number | string>> & {
  time?: number;
  slowest?: SlowestCase[];
  parser?: string;
  skipCategories?: Partial<SkipCategoryCounts>;
};

export type UnmetDependency = {
  key: string;
  type: string;
  value: string;
  satisfied: boolean;
  category: SkipCategory;
};

export type SkipInfo = {
  category: SkipCategory;
  dependencies: UnmetDependency[];
};

type RawDependencyItem = UnmetDependency & {
  count?: number;
  actionable?: number;
  exclusive?: number;
  suites?: Record<string, number>;
  cases?: string[];
};

export type TestCaseRuntime = {
  id: string;
  time: number;
};

export type RawIssueItem = {
  cases?: string[];
  message?: string;
  parser?: string;
};

type RawRegressionItem = {
  id?: string;
  status?: string;
  message?: string;
};

export type AnalysisPayload = {
  summary?: Record<string, RawCountRecord>;
  issues?: Record<string, Partial<Record<string, RawIssueItem[]>>>;
  regressions?: Record<string, RawRegressionItem[]>;
  improvements?: Record<string, string[]>;
  cases?: Record<string, TestCaseInfo>;
  skips?: {
    categories?: Partial<SkipCategoryCounts>;
    dependencies?: RawDependencyItem[];
  };
};

export type SuiteSummary = {
  name: string;
  pass: number;
  fail: number;
  error: number;
  skip: number;
  total: number;
  passRate: number;
  time: number;
  slowest: SlowestCase[];
  parser: string;
  skipCategories: SkipCategoryCounts;
};

export type Totals = {
  pass: number;
  fail: number;
  error: number;
  skip: number;
  total: number;
  passRate: number;
  time: number;
  skipCategories: SkipCategoryCounts;
  /** Tests that apply to RumbleDB: everything except not-applicable and other-spec skips. */
  applicable: number;
  /** Passing share of applicable tests, so missing-feature skips count against it. */
  applicablePassRate: number;
};

export type TestCaseInfo = {
  id: string;
  query?: string;
  translatedQueries?: string[];
  description?: string;
  expected?: string;
  status?: Status;
  type?: string;
  message?: string;
  detail?: string;
  skip?: SkipInfo;
};

export type DependencyRow = UnmetDependency & {
  label: string;
  count: number;
  actionable: number;
  exclusive: number;
  suites: { name: string; count: number }[];
  cases: TestCaseInfo[];
};

export type IssueRow = {
  suite: string;
  status: IssueStatus;
  message: string;
  count: number;
  cases: TestCaseInfo[];
  key: string;
  parser: string;
};

export type RegressionRow = {
  suite: string;
  id: string;
  status: Status;
  message: string;
  detail?: string;
  type?: string;
  query?: string;
  translatedQueries?: string[];
  description?: string;
  expected?: string;
};

export type ImprovementRow = {
  suite: string;
  id: string;
  message?: string;
};

export type ViewModel = {
  sourceName: string;
  suites: SuiteSummary[];
  totals: Totals;
  issueRows: IssueRow[];
  dependencyRows: DependencyRow[];
  regressions: RegressionRow[];
  improvements: ImprovementRow[];
};

export function buildViewModel(analysis: AnalysisPayload, sourceName: string): ViewModel {
  if (!analysis || typeof analysis !== "object") {
    throw new Error("Expected a JSON object.");
  }

  const suites = Object.entries(analysis.summary || {})
    .map(([name, counts]) => ({
      name,
      pass: toInt(counts.pass),
      fail: toInt(counts.fail),
      error: toInt(counts.error),
      skip: toInt(counts.skip),
      time: counts.time || 0,
      slowest: counts.slowest || [],
      parser: String(counts.parser || "jsoniq"),
      skipCategories: toSkipCategoryCounts(counts.skipCategories, toInt(counts.skip)),
    }))
    .map((suite) => ({
      ...suite,
      total: suite.pass + suite.fail + suite.error + suite.skip,
      passRate: percentNumber(suite.pass, suite.pass + suite.fail + suite.error),
    }))
    .sort((left, right) => right.total - left.total || left.name.localeCompare(right.name));

  const totals = suites.reduce<Omit<Totals, "passRate" | "applicable" | "applicablePassRate">>(
    (acc, suite) => ({
      pass: acc.pass + suite.pass,
      fail: acc.fail + suite.fail,
      error: acc.error + suite.error,
      skip: acc.skip + suite.skip,
      total: acc.total + suite.total,
      time: acc.time + suite.time,
      skipCategories: addSkipCategoryCounts(acc.skipCategories, suite.skipCategories),
    }),
    { pass: 0, fail: 0, error: 0, skip: 0, total: 0, time: 0, skipCategories: emptySkipCategoryCounts() }
  );
  const applicable =
    totals.total - totals.skipCategories["not-applicable"] - totals.skipCategories["other-spec"];

  return {
    sourceName,
    suites,
    totals: {
      ...totals,
      passRate: percentNumber(totals.pass, totals.pass + totals.fail + totals.error),
      applicable,
      applicablePassRate: percentNumber(totals.pass, applicable),
    },
    issueRows: flattenIssues(analysis.issues || {}, analysis.cases || {}),
    dependencyRows: flattenDependencies(analysis.skips?.dependencies || [], analysis.cases || {}),
    regressions: flattenRegressions(analysis.regressions || {}, analysis.cases || {}),
    improvements: flattenImprovements(analysis.improvements || {}),
  };
}

export function formatDependency(dependency: Pick<UnmetDependency, "type" | "value" | "satisfied">): string {
  const subject = dependency.type === "spec" ? `${dependency.value} only` : dependency.value;
  const prefix = dependency.type === "feature" || dependency.type === "spec" ? "" : `${dependency.type} `;
  return dependency.satisfied ? `${prefix}${subject}` : `${prefix}${subject} (must be absent)`;
}

export type StatusSegment = {
  key: Exclude<Status, "SKIP"> | SkipCategory;
  label: string;
  count: number;
  color: string;
};

/** Pass/fail/error followed by the skip categories, so skips are never shown as one opaque block. */
export function statusSegments(counts: Pick<Totals, "pass" | "fail" | "error" | "skipCategories">): StatusSegment[] {
  return [
    { key: "PASS" as const, label: "Pass", count: counts.pass, color: "var(--pass)" },
    { key: "FAIL" as const, label: "Fail", count: counts.fail, color: "var(--fail)" },
    { key: "ERROR" as const, label: "Error", count: counts.error, color: "var(--error)" },
    ...SKIP_CATEGORY_ORDER.map((category) => ({
      key: category,
      label: SKIP_CATEGORY_META[category].label,
      count: counts.skipCategories[category],
      color: SKIP_CATEGORY_META[category].color,
    })),
  ];
}

export function suiteOfCase(caseId: string): string {
  const slash = caseId.indexOf("/");
  return slash > 0 ? caseId.slice(0, slash) : "unknown";
}

export function formatPercent(value: number): string {
  return `${Number(value || 0).toFixed(1)}%`;
}

export function formatDuration(sec: number): string {
  if (sec < 0.001) return "< 1ms";
  if (sec < 1) return `${(sec * 1000).toFixed(0)}ms`;
  if (sec < 60) return `${sec.toFixed(2)}s`;
  
  const hrs = Math.floor(sec / 3600);
  const mins = Math.floor((sec % 3600) / 60);
  const secs = sec % 60;
  
  if (hrs > 0) {
    return `${hrs}h ${mins}m ${secs.toFixed(0)}s`;
  }
  return `${mins}m ${secs.toFixed(0)}s`;
}

export function percentNumber(value: number, total: number): number {
  return Number(((value / Math.max(total, 1)) * 100).toFixed(1));
}

export function getSuiteClassName(suiteName: string): string {
  if (!suiteName) return "";
  const lower = suiteName.toLowerCase();
  if (lower === "prod") return "Prod1Test,Prod2Test";
  if (lower === "fn") return "Fn1Test,Fn2Test";
  return `${suiteName.charAt(0).toUpperCase()}${suiteName.slice(1)}Test`;
}

export function getParserCommand(issue: IssueRow | null): string {
  if (!issue?.suite) return "";
  const suiteClass = getSuiteClassName(issue.suite);
  const parser = issue.parser || "jsoniq";
  if (parser === "default") {
    return `mvn -Dtest=${suiteClass} test`;
  }
  return `mvn -Dtest=${suiteClass} -Dparser=${parser} test`;
}

export function getSingleTestCaseCommand(suiteName: string, caseId: string, parser?: string): string {
  if (!suiteName || !caseId) return "";
  const suiteClass = getSuiteClassName(suiteName);
  
  // Extract test name (the part after the colon)
  let testCaseName = caseId;
  const colonIndex = caseId.indexOf(":");
  if (colonIndex !== -1) {
    testCaseName = caseId.slice(colonIndex + 1);
  }
  
  // Clean up any characters that might interfere with Maven matching
  testCaseName = testCaseName.replace(/[*?()\[\]]/g, "");
  
  const parserArg = (!parser || parser === "default") ? "" : ` -Dparser=${parser}`;
  return `mvn -Dtest=${suiteClass} -Dtest.case=${testCaseName}${parserArg} test`;
}

export function decodeExpectedResult(value: string | undefined): string | undefined {
  if (!value) {
    return value;
  }

  return value.replace(/&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);/g, (match, entity) => {
    switch (entity) {
      case "amp":
        return "&";
      case "lt":
        return "<";
      case "gt":
        return ">";
      case "quot":
        return '"';
      case "apos":
        return "'";
      default:
        if (entity.startsWith("#x")) {
          const codePoint = Number.parseInt(entity.slice(2), 16);
          return Number.isNaN(codePoint) ? match : String.fromCodePoint(codePoint);
        }
        if (entity.startsWith("#")) {
          const codePoint = Number.parseInt(entity.slice(1), 10);
          return Number.isNaN(codePoint) ? match : String.fromCodePoint(codePoint);
        }
        return match;
    }
  });
}


function flattenIssues(
  issuesBySuite: AnalysisPayload["issues"],
  casesMap: Record<string, TestCaseInfo>
): IssueRow[] {
  const rows: IssueRow[] = [];
  for (const [suiteName, statuses] of Object.entries(issuesBySuite || {})) {
    for (const [statusKey, items] of Object.entries(statuses || {})) {
      const status = toStatus(statusKey);
      // Reports produced before skips moved to their own section still contain skip groups.
      if (status !== "FAIL" && status !== "ERROR") {
        continue;
      }
      for (const item of items || []) {
        const caseIds = Array.isArray(item.cases) ? item.cases : [];
        const cases: TestCaseInfo[] = caseIds.map((id) => {
          const details = casesMap[id] || {};
          return {
            id,
            query: details.query,
            translatedQueries: details.translatedQueries,
            description: details.description,
            expected: decodeExpectedResult(details.expected),
            status: details.status,
            type: details.type,
            message: details.message,
            detail: details.detail,
          };
        });
        const message = item.message || "(no message)";
        const parser = item.parser || "jsoniq";
        rows.push({
          suite: suiteName,
          status,
          message,
          count: cases.length,
          cases,
          parser,
          key: `${status}::${suiteName}::${message}`,
        });
      }
    }
  }

  rows.sort(
    (left, right) =>
      right.count - left.count ||
      left.suite.localeCompare(right.suite) ||
      left.message.localeCompare(right.message)
  );
  return rows;
}

function flattenRegressions(
  regressionsBySuite: AnalysisPayload["regressions"],
  casesMap: Record<string, TestCaseInfo>
): RegressionRow[] {
  const rows: RegressionRow[] = [];
  for (const [suiteName, cases] of Object.entries(regressionsBySuite || {})) {
    for (const item of cases || []) {
      const status = toStatus(item.status) || "FAIL";
      const id = item.id || "";
      const details = casesMap[id] || {};
      rows.push({
        suite: suiteName,
        id,
        status,
        message: item.message || details.message || "(no message)",
        detail: details.detail,
        type: details.type,
        query: details.query,
        translatedQueries: details.translatedQueries,
        description: details.description,
        expected: decodeExpectedResult(details.expected),
      });
    }
  }

  rows.sort(
    (left, right) =>
      left.suite.localeCompare(right.suite) ||
      left.status.localeCompare(right.status) ||
      left.id.localeCompare(right.id)
  );
  return rows;
}

export function findIssueKeyForCase(viewModel: ViewModel, suiteName: string, caseId: string): string | null {
  for (const issue of viewModel.issueRows) {
    if (issue.suite === suiteName && issue.cases.some((c) => c.id === caseId)) {
      return issue.key;
    }
  }
  // Fallback to any issue in suite
  const suiteIssue = viewModel.issueRows.find((i) => i.suite === suiteName);
  return suiteIssue ? suiteIssue.key : null;
}

function flattenImprovements(improvementsBySuite: AnalysisPayload["improvements"]): ImprovementRow[] {
  const rows: ImprovementRow[] = [];
  for (const [suiteName, ids] of Object.entries(improvementsBySuite || {})) {
    for (const id of ids || []) {
      rows.push({
        suite: suiteName,
        id: id || "",
      });
    }
  }

  rows.sort((left, right) => left.suite.localeCompare(right.suite) || left.id.localeCompare(right.id));
  return rows;
}

function flattenDependencies(
  items: RawDependencyItem[],
  casesMap: Record<string, TestCaseInfo>
): DependencyRow[] {
  return items.map((item) => ({
    key: item.key,
    type: item.type,
    value: item.value,
    satisfied: item.satisfied !== false,
    category: toSkipCategory(item.category),
    label: formatDependency(item),
    count: toInt(item.count),
    actionable: toInt(item.actionable),
    exclusive: toInt(item.exclusive),
    suites: Object.entries(item.suites || {})
      .map(([name, count]) => ({ name, count: toInt(count) }))
      .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name)),
    cases: (item.cases || []).map((id) => ({ ...casesMap[id], id })),
  }));
}

function emptySkipCategoryCounts(): SkipCategoryCounts {
  return { "missing-feature": 0, "not-applicable": 0, "other-spec": 0, unclassified: 0 };
}

/** Reports without skip categories treat every skip as unclassified. */
function toSkipCategoryCounts(raw: Partial<SkipCategoryCounts> | undefined, skip: number): SkipCategoryCounts {
  const counts = emptySkipCategoryCounts();
  if (!raw) {
    counts.unclassified = skip;
    return counts;
  }
  for (const category of SKIP_CATEGORY_ORDER) {
    counts[category] = toInt(raw[category]);
  }
  return counts;
}

function addSkipCategoryCounts(left: SkipCategoryCounts, right: SkipCategoryCounts): SkipCategoryCounts {
  const counts = emptySkipCategoryCounts();
  for (const category of SKIP_CATEGORY_ORDER) {
    counts[category] = left[category] + right[category];
  }
  return counts;
}

function toSkipCategory(value: string | undefined): SkipCategory {
  return (SKIP_CATEGORY_ORDER as readonly string[]).includes(String(value)) ? (value as SkipCategory) : "unclassified";
}

function toInt(value: number | string | undefined): number {
  return Number.parseInt(String(value || 0), 10) || 0;
}

function toStatus(value: string | undefined): Status | null {
  const normalized = String(value || "").toUpperCase();
  return (STATUS_ORDER as readonly string[]).includes(normalized) ? (normalized as Status) : null;
}
