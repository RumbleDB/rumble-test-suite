package evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rumbledb.api.Rumble;
import org.rumbledb.config.CompilationConfiguration;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.resources.ResolvedResource;

public class EnvironmentTest {

    @TempDir
    Path directory;

    @Test
    public void resolvesModulesAndSchemasWithoutRewritingTheirLogicalUris() throws Exception {
        Path module = Files.writeString(this.directory.resolve("module.xq"), "module namespace m = \"urn:module\";");
        Path schema = Files.writeString(this.directory.resolve("schema.xsd"), "<schema/>");
        Environment environment = environmentWithImports(
            "<test-case>"
                + "<module uri=\"urn:module\" file=\"module.xq\"/>"
                + "<schema uri=\"urn:schema\" file=\"schema.xsd\"/>"
                + "</test-case>"
        );

        String query = "import module namespace m = \"urn:module\"; 1";
        assertEquals(query, environment.applyToQuery(query));
        assertResolvedLocation(environment, URI.create("urn:module"), module);
        assertResolvedLocation(environment, URI.create("urn:schema"), schema);
    }

    @Test
    public void usesTheEnvironmentResolverWhenCompilingAnImportedModule() throws Exception {
        Files.writeString(
            this.directory.resolve("module.xq"),
            "module namespace m = \"urn:module\"; declare function m:value() { 42 };"
        );
        Environment environment = environmentWithImports(
            "<test-case><module uri=\"urn:module\" file=\"module.xq\"/></test-case>"
        );
        RumbleRuntimeConfiguration runtimeConfiguration = new RumbleRuntimeConfiguration(
                new String[] { "--default-language", "xquery31" }
        );

        int value = new Rumble(
                new CompilationConfiguration(runtimeConfiguration, environment.getResourceResolver())
        ).runQuery("import module namespace m = \"urn:module\"; m:value()")
            .getAsList()
            .get(0)
            .getIntValue();

        assertEquals(42, value);
    }

    @Test
    public void acceptsMalformedLogicalUrisUsedByNegativeTests() throws Exception {
        environmentWithImports(
            "<test-case><module uri=\"http://example.com/invalid uri\" file=\"module.xq\"/></test-case>"
        );
    }

    @Test
    public void doesNotMutateASharedEnvironment() throws Exception {
        Path fallback = Files.writeString(this.directory.resolve("fallback.xq"), "fallback");
        Path replacement = Files.writeString(this.directory.resolve("replacement.xq"), "replacement");
        Environment shared = new Environment(element("<environment/>", "environment"), this.directory);
        XdmNode testCase = element(
            "<test-case><module uri=\""
                + fallback.toUri()
                + "\" file=\"replacement.xq\"/></test-case>",
            "test-case"
        );

        Environment extended = Environment.forTestCase(shared, testCase, this.directory);

        assertResolvedLocation(shared, fallback.toUri(), fallback);
        assertResolvedLocation(extended, fallback.toUri(), replacement);
    }

    private Environment environmentWithImports(String xml) throws SaxonApiException {
        XdmNode testCase = element(xml, "test-case");
        return Environment.forTestCase(null, testCase, this.directory);
    }

    private static XdmNode element(String xml, String name) throws SaxonApiException {
        Processor processor = new Processor(false);
        DocumentBuilder builder = processor.newDocumentBuilder();
        XdmNode document = builder.build(new StreamSource(new StringReader(xml)));
        return document.children(name).iterator().next();
    }

    private static void assertResolvedLocation(Environment environment, URI logicalUri, Path expected)
            throws Exception {
        try (
            ResolvedResource resource = environment.getResourceResolver()
                .resolve(logicalUri, new RumbleRuntimeConfiguration(), ExceptionMetadata.EMPTY_METADATA)
        ) {
            assertEquals(expected.toUri(), resource.getSystemId());
            resource.getInputStream().readAllBytes();
        }
    }
}
