package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class EnvironmentQueryRewriterTest {

    @Test
    public void replacesOnlyCompleteStringLiteralValues() {
        String query = "\"urn:test\", \"prefix urn:test\", (: \"urn:test\" :) <e>urn:test</e>";

        assertEquals(
            "\"file:///resource.xml\", \"prefix urn:test\", (: \"urn:test\" :) <e>urn:test</e>",
            EnvironmentQueryRewriter.rewrite(
                query,
                "",
                Map.of(),
                Map.of("urn:test", "file:///resource.xml")
            )
        );
    }

    @Test
    public void bindsOnlyTheMatchingExternalVariableDeclaration() {
        String query = "(: declare variable $value external; :)\n"
            + "declare variable $value external;\n"
            + "declare variable $value-more external;\n"
            + "\"declare variable $value external;\"";

        assertEquals(
            "(: declare variable $value external; :)\n"
                + "declare variable $value external := (42);\n"
                + "declare variable $value-more external;\n"
                + "\"declare variable $value external;\"",
            EnvironmentQueryRewriter.rewrite(query, "", Map.of("value", "42"), Map.of())
        );
    }

    @Test
    public void preservesAnExistingExternalDefault() {
        String query = "declare variable $value external := 1; $value";

        assertEquals(
            query,
            EnvironmentQueryRewriter.rewrite(query, "", Map.of("value", "42"), Map.of())
        );
    }

    @Test
    public void insertsDeclarationsBetweenLeadingAndAnnotatedPrologDeclarations() {
        String query = "xquery version \"3.1\";\n"
            + "declare namespace ex = \"urn:\"\"example;\";\n"
            + "(: keep with the existing variable :)\n"
            + "declare variable $existing external;\n"
            + "$existing";

        assertEquals(
            "xquery version \"3.1\";\n"
                + "declare namespace ex = \"urn:\"\"example;\";\n"
                + "(: keep with the existing variable :)\n"
                + "declare variable $environment := 1;"
                + "declare variable $existing external;\n"
                + "$existing",
            EnvironmentQueryRewriter.rewrite(
                query,
                "declare variable $environment := 1;",
                Map.of(),
                Map.of()
            )
        );
    }

    @Test
    public void replacesResourcesInsideInjectedValues() {
        String query = "declare variable $external external; $external";

        assertEquals(
            "declare variable $environment := \"file:///resource.xml\";"
                + "declare variable $external external := (\"file:///resource.xml\"); $external",
            EnvironmentQueryRewriter.rewrite(
                query,
                "declare variable $environment := \"urn:test\";",
                Map.of("external", "\"urn:test\""),
                Map.of("urn:test", "file:///resource.xml")
            )
        );
    }

    @Test
    public void leavesInvalidXQueryUntouched() {
        String query = "\"urn:test";

        assertEquals(
            query,
            EnvironmentQueryRewriter.rewrite(
                query,
                "declare variable $environment := 1;",
                Map.of(),
                Map.of("urn:test", "file:///resource.xml")
            )
        );
    }
}
