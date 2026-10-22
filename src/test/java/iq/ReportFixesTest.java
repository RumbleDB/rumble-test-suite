package iq;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import evaluation.CaseCollector;
import evaluation.TestCaseSelection;
import iq.base.TestBase;

/** Targeted QT3 cases from the October 22 report, using a read-only local checkout. */
@EnabledIfSystemProperty(named = "qt3.repository", matches = ".+")
public class ReportFixesTest extends TestBase {
    private static final Set<String> CASES = Set.of(
            "K2-ancestorAxis-8",
            "K2-ancestorAxis-11",
            "K2-ancestor-or-selfAxis-9",
            "K2-precedingAxis-3",
            "K2-precedingAxis-6",
            "K2-RangeExpr-4",
            "RangeExpr-604",
            "last-24",
            "position-23",
            "contextDecl-016",
            "contextDecl-017",
            "extvardef-016a",
            "extvardef-016b",
            "fn-random-number-generator-30",
            "MapTest-061",
            "MapTest-062",
            "MapTest-063",
            "MapTest-066",
            "ArrayTest-066",
            "ArrayTest-086",
            "K-Literals-29",
            "K-Literals-30",
            "K2-Literals-20",
            "K2-Literals-34",
            "K2-Literals-35",
            "K2-Literals-36",
            "K2-Axes-97",
            "extvardef-001a",
            "extvardef-002a",
            "extvardef-002b",
            "extvardef-003a",
            "extvardef-003b",
            "extvardef-006a",
            "contextDecl-040",
            "contextDecl-041",
            "fn-collection-7",
            "collection-001",
            "collection-002",
            "collection-003",
            "collection-005",
            "collection-006",
            "collection-007",
            "collection-009",
            "cbcl-collection-002",
            "cbcl-collection-003",
            "cbcl-collection-004",
            "fn-uri-collection-2",
            "fn-uri-collection-3",
            "K2-SeqUriCollectionFunc-1",
            "K2-SeqUriCollectionFunc-2",
            "uri-collection-901",
            "uri-collection-903",
            "UseCaseR31-026",
            "UseCaseR31-027",
            "Axes085",
            "Constr-cont-cdata-1",
            "K2-NameTest-47");

    @TestFactory
    Stream<DynamicTest> reportCases() throws Exception {
        var collector = new CaseCollector(TestCaseSelection.fromSystemProperties());
        collector.execute(
                Path.of(System.getProperty("qt3.repository")),
                "(?:fn/(?:last|position|random-number-generator|collection|uri-collection)|op/to|prod/(?:AxisStep[^/]*|ContextItemDecl|VarDefaultValue|MapTest|ArrayTest|Literal|DirElemContent|NameTest)|app/UseCaseR31)\\.");
        var cases = collector.getAllTests().stream()
                .filter(test -> CASES.contains(test.testCaseName()))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(
                CASES.size(),
                cases.size(),
                "Missing cases: "
                        + CASES.stream()
                                .filter(name -> cases.stream()
                                        .noneMatch(test -> test.testCaseName().equals(name)))
                                .toList());
        return cases.stream().map(test -> DynamicTest.dynamicTest(test.toString(), () -> testCase(test)));
    }
}
