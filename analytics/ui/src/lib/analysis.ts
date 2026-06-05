export const STATUS_ORDER = ["PASS", "FAIL", "ERROR", "SKIP"] as const;

export type Status = (typeof STATUS_ORDER)[number];
export type StatusFilter = Status | "ALL";
export type ParserMode = "jsoniq" | "xquery" | "default";

export type SlowestCase = {
  id: string;
  time: number;
  status: string;
};

export type RawCountRecord = Partial<Record<Lowercase<Status>, number | string>> & {
  time?: number;
  slowest?: SlowestCase[];
};

export type TestCaseRuntime = {
  id: string;
  time: number;
};

export type RawIssueItem = {
  cases?: string[];
  message?: string;
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
  cases?: Record<string, { query?: string; description?: string; expected?: string }>;
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
};

export type Totals = {
  pass: number;
  fail: number;
  error: number;
  skip: number;
  total: number;
  passRate: number;
  time: number;
};

export type TestCaseInfo = {
  id: string;
  query?: string;
  description?: string;
  expected?: string;
};

export type IssueRow = {
  suite: string;
  status: Status;
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
  query?: string;
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
      parser: String((counts as any).parser || "jsoniq"),
    }))
    .map((suite) => ({
      ...suite,
      total: suite.pass + suite.fail + suite.error + suite.skip,
      passRate: percentNumber(suite.pass, suite.pass + suite.fail + suite.error + suite.skip),
    }))
    .sort((left, right) => right.total - left.total || left.name.localeCompare(right.name));

  const totals = suites.reduce<Totals>(
    (acc, suite) => ({
      pass: acc.pass + suite.pass,
      fail: acc.fail + suite.fail,
      error: acc.error + suite.error,
      skip: acc.skip + suite.skip,
      total: acc.total + suite.total,
      time: acc.time + suite.time,
      passRate: 0,
    }),
    { pass: 0, fail: 0, error: 0, skip: 0, total: 0, time: 0, passRate: 0 }
  );

  return {
    sourceName,
    suites,
    totals: {
      ...totals,
      passRate: percentNumber(totals.pass, totals.total),
    },
    issueRows: flattenIssues(analysis.issues || {}, analysis.cases || {}),
    regressions: flattenRegressions(analysis.regressions || {}, analysis.cases || {}),
    improvements: flattenImprovements(analysis.improvements || {}),
  };
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
  return `${suiteName.charAt(0).toUpperCase()}${suiteName.slice(1)}Test`;
}

export function getParserCommand(issue: IssueRow | null, parserMode: ParserMode): string {
  if (!issue?.suite) return "";
  const suiteClass = getSuiteClassName(issue.suite);
  if (parserMode === "default") {
    return `mvn -Dtest=${suiteClass} test`;
  }
  return `mvn -Dtest=${suiteClass} -Dparser=${parserMode} test`;
}

export function getSingleTestCaseCommand(suiteName: string, caseId: string, parserMode: ParserMode): string {
  if (!suiteName || !caseId) return "";
  const suiteClass = getSuiteClassName(suiteName);
  
  // Extract test name (the part after the colon)
  // Example caseId: "ser/method-json.xml:Serialization-json-59" -> "Serialization-json-59"
  let testCaseName = caseId;
  const colonIndex = caseId.indexOf(":");
  if (colonIndex !== -1) {
    testCaseName = caseId.slice(colonIndex + 1);
  }
  
  // Clean up any characters that might interfere with Maven matching
  testCaseName = testCaseName.replace(/[*?()\[\]]/g, "");
  
  const parserArg = parserMode === "default" ? "" : ` -Dparser=${parserMode}`;
  return `mvn -Dtest=${suiteClass} -Dtest.case=${testCaseName}${parserArg} test`;
}


function flattenIssues(
  issuesBySuite: AnalysisPayload["issues"],
  casesMap: Record<string, { query?: string; description?: string; expected?: string }>
): IssueRow[] {
  const rows: IssueRow[] = [];
  for (const [suiteName, statuses] of Object.entries(issuesBySuite || {})) {
    for (const [statusKey, items] of Object.entries(statuses || {})) {
      const status = toStatus(statusKey);
      if (!status) {
        continue;
      }
      for (const item of items || []) {
        const caseIds = Array.isArray(item.cases) ? item.cases : [];
        const cases: TestCaseInfo[] = caseIds.map((id) => {
          const details = casesMap[id] || {};
          return {
            id,
            query: details.query,
            description: details.description,
            expected: details.expected,
          };
        });
        const message = item.message || "(no message)";
        const parser = (item as any).parser || "jsoniq";
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
  casesMap: Record<string, { query?: string; description?: string; expected?: string }>
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
        message: item.message || "",
        query: details.query,
        description: details.description,
        expected: details.expected,
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

function toInt(value: number | string | undefined): number {
  return Number.parseInt(String(value || 0), 10) || 0;
}

function toStatus(value: string | undefined): Status | null {
  const normalized = String(value || "").toUpperCase();
  return (STATUS_ORDER as readonly string[]).includes(normalized) ? (normalized as Status) : null;
}
