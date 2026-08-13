package evaluation.conversion;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnvironmentQueryRewriterTest {

    @Test
    public void replacesOnlyCompleteStringLiteralValues() {
        String query = "\"urn:test\", \"prefix urn:test\", (: \"urn:test\" :) <e>urn:test</e>";

        assertEquals(
                "\"file:///resource.xml\", \"prefix urn:test\", (: \"urn:test\" :) <e>urn:test</e>",
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of(),
                        "",
                        Map.of(),
                        Map.of("urn:test", "file:///resource.xml"),
                        Map.of(),
                        Map.of(),
                        false));
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
                EnvironmentQueryRewriter.rewrite(
                        query, Map.of(), "", Map.of("value", "42"), Map.of(), Map.of(), Map.of(), false));
    }

    @Test
    public void preservesAnExistingExternalDefault() {
        String query = "declare variable $value external := 1; $value";

        assertEquals(
                query,
                EnvironmentQueryRewriter.rewrite(
                        query, Map.of(), "", Map.of("value", "42"), Map.of(), Map.of(), Map.of(), false));
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
                        Map.of(),
                        "declare variable $environment := 1;",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        false));
    }

    @Test
    public void replacesResourcesInsideInjectedValues() {
        String query = "declare variable $external external; $external";

        assertEquals(
                "declare variable $environment := \"file:///resource.xml\";"
                        + "declare variable $external external := (\"file:///resource.xml\"); $external",
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of(),
                        "declare variable $environment := \"urn:test\";",
                        Map.of("external", "\"urn:test\""),
                        Map.of("urn:test", "file:///resource.xml"),
                        Map.of(),
                        Map.of(),
                        false));
    }

    @Test
    public void leavesInvalidXQueryUntouched() {
        String query = "\"urn:test";

        assertEquals(
                query,
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of(),
                        "declare variable $environment := 1;",
                        Map.of(),
                        Map.of("urn:test", "file:///resource.xml"),
                        Map.of(),
                        Map.of(),
                        false));
    }

    @Test
    public void injectsEnvironmentModuleLocationsIntoModuleImports() {
        String query = "import module namespace m=\"urn:module\"; 1";

        assertEquals(
                "import module namespace m=\"urn:module\" at \"file:///module1.xq\", \"file:///module2.xq\"; 1",
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of(),
                        "",
                        Map.of(),
                        Map.of(),
                        Map.of("urn:module", java.util.List.of("file:///module1.xq", "file:///module2.xq")),
                        Map.of(),
                        false));
    }

    @Test
    public void doesNotDuplicateNamespaceBoundBySchemaImport() {
        String query = "import schema namespace atomic=\"urn:atomic\"; \"ABC\"";

        assertEquals(
                "import schema namespace atomic=\"urn:atomic\"; "
                        + "declare context item := doc(\"file:///atomic.xml\"); \"ABC\"",
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of("atomic", "urn:atomic"),
                        "declare context item := doc(\"file:///atomic.xml\"); ",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        false));
    }

    @Test
    public void replacesExistingSchemaImportLocationsWithoutInjectingAnotherImport() {
        String query = "import schema namespace s = \"urn:schema\"; 1";

        assertEquals(
                "import schema namespace s = \"urn:schema\" at \"file:///schema.xsd\"; 1",
                EnvironmentQueryRewriter.rewrite(
                        query,
                        Map.of(),
                        "",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of("urn:schema", java.util.List.of("file:///schema.xsd")),
                        false));
    }

    @Test
    public void doesNotInjectEnvironmentSchemaWithoutAValidatedSource() {
        assertEquals(
                "1",
                EnvironmentQueryRewriter.rewrite(
                        "1",
                        Map.of(),
                        "",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of("urn:schema", java.util.List.of("file:///schema.xsd")),
                        false));
    }

    @Test
    public void doesNotDuplicateNamespaceBoundByModuleImport() {
        String query = "import module namespace module=\"urn:module\"; \"ABC\"";

        assertEquals(
                query,
                EnvironmentQueryRewriter.rewrite(
                        query, Map.of("module", "urn:module"), "", Map.of(), Map.of(), Map.of(), Map.of(), false));
    }

    @Test
    public void rejectsConflictingEnvironmentAndQueryNamespaceBindings() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EnvironmentQueryRewriter.rewrite(
                        "import schema namespace atomic=\"urn:query\"; \"ABC\"",
                        Map.of("atomic", "urn:environment"),
                        "",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        false));

        assertEquals(
                "QT3 environment binds prefix atomic to urn:environment, but the query binds it to urn:query.",
                exception.getMessage());
    }
}
