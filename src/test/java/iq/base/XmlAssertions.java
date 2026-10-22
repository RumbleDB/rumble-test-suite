package iq.base;

import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;

/** Whitespace-insensitive XML comparison without XMLUnit's namespace-sensitive DOM cloning. */
final class XmlAssertions {
    private XmlAssertions() {}

    static Diff compare(String expected, String actual, boolean ignorePrefixes) {
        return DiffBuilder.compare(expected)
                .withTest(actual)
                .withNodeFilter(node -> node.getNodeType() != Node.TEXT_NODE
                        || !node.getNodeValue().isBlank())
                .withDifferenceEvaluator((comparison, outcome) -> {
                    if (ignorePrefixes && comparison.getType() == ComparisonType.NAMESPACE_PREFIX) {
                        return ComparisonResult.EQUAL;
                    }
                    if (comparison.getType() == ComparisonType.TEXT_VALUE
                            && comparison
                                    .getControlDetails()
                                    .getValue()
                                    .toString()
                                    .trim()
                                    .equals(comparison
                                            .getTestDetails()
                                            .getValue()
                                            .toString()
                                            .trim())) {
                        return ComparisonResult.EQUAL;
                    }
                    return outcome;
                })
                .build();
    }
}
