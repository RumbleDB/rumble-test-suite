package driver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * contains constants used throughout the code like
 * Test cases to skip, Test sets to skip,
 * Type conversions, Unsupported Types
 * Supported error codes
 */
public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();

    public static final List<String> skippedTestSets = List.of(
        "fn/subsequence.xml", // contains large testcases that take forever to run
        "prod/WindowClause.xml" // we dont support window yet

    );

    public static final List<String> skippedTestCases = List.of(
        "fn-distinct-values-2" // does not terminate
    );

    public static final Map<String, String> atomicTypeConversions = Map.ofEntries(
        Map.entry("xs:atomic", "atomic")
    );

    public static final Map<String, String> nonAtomicTypeConversions = Map.ofEntries(
        // Also array(+), array(?), array()*, array()+, array()? do not exist
        Map.entry("array(*)", "array*"),

        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        Map.entry("item()", "item"),

        // These are not types but instantiations of boolean handled differently
        Map.entry("true()", "true"),
        Map.entry("false()", "false"),

        Map.entry("map(", "object("),
        Map.entry("map{", "{"),
        Map.entry("map {", " {")
    );

    public static final String[] supportedErrorCodes = new String[] {
        "FOAR0001",
        "FOCA0002",
        "FOCA0005",
        "FOCH0001",
        "FOCH0002",
        "FOCH0003",
        "FODC0002",
        "FOER0000",
        "FOFD1340",
        "FOFD1350",
        "FORG0001",
        "FORG0003",
        "FORG0004",
        "FORG0005",
        "FORG0006",
        "FORG0008",
        "FORX0002",
        "FORX0003",
        "FORX0004",
        "FOTY0012",
        "JNDY0003",
        "JNTY0024",
        "JNTY0004",
        "JNTY0018",
        "RBDY0005",
        "RBML0001",
        "RBML0002",
        "RBML0003",
        "RBML0004",
        "RBST0001",
        "RBST0002",
        "RBST0003",
        "RBST0004",
        "RBDY0005",
        "RBDY0006",
        "RBDY0007",
        "RBTY0001",
        "SENR0001",
        "XPDY0002",
        "XPDY0050",
        "XPDY0130",
        "XPST0003",
        "XPST0005",
        "XPST0008",
        "XPST0017",
        "XPST0051",
        "XPST0080",
        "XPST0081",
        "XPTY0004",
        "XQDY0027",
        "XQDY0054",
        "XQST0012",
        "XQST0016",
        "XQST0031",
        "XQST0033",
        "XQST0034",
        "XQST0038",
        "XQST0039",
        "XQST0047",
        "XQST0048",
        "XQST0049",
        "XQST0052",
        "XQST0059",
        "XQST0069",
        "XQST0088",
        "XQST0089",
        "XQST0094",
        "FOTY0012",
        "FOTY0015",
        "FODT0002",
        "FODT0003",
        "XUST0001",
        "XUST0002",
        "XUTY0013",
        "XUDY0014",
        "XUDY0027",
        "XUST0028",
        "JNUP0005",
        "JNUP0006",
        "JNUP0007",
        "JNUP0008",
        "JNUP0009",
        "JNUP0010",
        "JNUP0016",
        "JNUP0019",
        "SCCL0001",
        "SCCP0001",
        "SCCP0002",
        "SCCP0003",
        "SCCP0004",
        "SCCP0005",
        "SCCP0006",
        "XQAN0001",
        "SCIN0001",
        "XPTY0019"
    };
}
