package evaluation;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.rumbledb.api.Rumble;
import org.rumbledb.config.CompilationConfiguration;
import org.rumbledb.config.RumbleConfiguration;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.resources.ResolvedResource;

public class EnvironmentTest {

    @TempDir
    Path directory;

    @Test
    public void rewritesEnvironmentModulesToExplicitLocationHints() throws Exception {
        Path module = Files.writeString(this.directory.resolve("module.xq"), "module namespace m = \"urn:module\";");
        Path schema = Files.writeString(this.directory.resolve("schema.xsd"), "<schema/>");
        Environment environment = environmentWithImports("<test-case>"
                + "<module uri=\"urn:module\" file=\"module.xq\"/>"
                + "<schema uri=\"urn:schema\" file=\"schema.xsd\"/>"
                + "</test-case>");

        String query = "import module namespace m = \"urn:module\"; 1";
        assertEquals(
                "import module namespace m=\"urn:module\" at \"" + module.toUri() + "\"; 1",
                environment.applyToQuery(query));
        assertResolvedLocation(environment, URI.create("urn:module"), module);
        assertResolvedLocation(environment, URI.create("urn:schema"), schema);
    }

    @Test
    public void rewritesSameNamespaceEnvironmentModulesIntoMultipleLocationHints() throws Exception {
        Files.writeString(this.directory.resolve("module1.xq"), "module namespace m = \"urn:module\";");
        Files.writeString(this.directory.resolve("module2.xq"), "module namespace m = \"urn:module\";");
        Environment environment = environmentWithImports("<test-case>"
                + "<module uri=\"urn:module\" file=\"module1.xq\"/>"
                + "<module uri=\"urn:module\" file=\"module2.xq\"/>"
                + "</test-case>");

        assertEquals(
                "import module namespace m=\"urn:module\" at \""
                        + this.directory.resolve("module1.xq").toUri()
                        + "\", \""
                        + this.directory.resolve("module2.xq").toUri()
                        + "\"; 1",
                environment.applyToQuery("import module namespace m=\"urn:module\"; 1"));
    }

    @Test
    public void usesTheEnvironmentResolverWhenCompilingAnImportedModule() throws Exception {
        Files.writeString(
                this.directory.resolve("module.xq"),
                "module namespace m = \"urn:module\"; declare function m:value() { 42 };");
        Environment environment =
                environmentWithImports("<test-case><module uri=\"urn:module\" file=\"module.xq\"/></test-case>");
        RumbleConfiguration runtimeConfiguration = RumbleConfiguration.builder()
                .configureSemantics(semantics -> semantics.queryLanguage("xquery31"))
                .build();

        int value = new Rumble(new CompilationConfiguration(runtimeConfiguration, environment.getResourceResolver()))
                .runQuery("import module namespace m = \"urn:module\"; m:value()")
                .getAsList()
                .get(0)
                .getIntValue();

        assertEquals(42, value);
    }

    @Test
    public void acceptsMalformedLogicalUrisUsedByNegativeTests() throws Exception {
        environmentWithImports(
                "<test-case><module uri=\"http://example.com/invalid uri\" file=\"module.xq\"/></test-case>");
    }

    @Test
    public void preservesSourceValidationModesInInjectedDeclarations() throws Exception {
        Path strict = Files.writeString(this.directory.resolve("strict.xml"), "<root/>");
        Path lax = Files.writeString(this.directory.resolve("lax.xml"), "<root/>");
        Environment environment = new Environment(
                element(
                        "<environment>"
                                + "<source role=\".\" file=\"strict.xml\" validation=\"strict\"/>"
                                + "<source role=\"$other\" file=\"lax.xml\" validation=\"lax\"/>"
                                + "</environment>",
                        "environment"),
                this.directory);

        assertEquals(
                "declare context item := validate strict { fn:doc(\""
                        + strict.toUri()
                        + "\") }; declare variable $other := validate lax { fn:doc(\""
                        + lax.toUri()
                        + "\") }; 1",
                environment.applyToQuery("1"));
    }

    @Test
    public void importsEnvironmentSchemasWithoutBindingTheirNamespaces() throws Exception {
        Path schema = Files.writeString(this.directory.resolve("schema.xsd"), "<schema/>");
        Path noNamespaceSchema = Files.writeString(this.directory.resolve("no-namespace-schema.xsd"), "<schema/>");
        Environment environment = new Environment(
                element(
                        "<environment>"
                                + "<schema uri=\"urn:schema\" file=\"schema.xsd\"/>"
                                + "<schema file=\"no-namespace-schema.xsd\"/>"
                                + "<source role=\".\" file=\"document.xml\" validation=\"strict\"/>"
                                + "</environment>",
                        "environment"),
                this.directory);

        assertEquals(
                "import schema \"urn:schema\" at \""
                        + schema.toUri()
                        + "\";\n"
                        + "import schema \"\" at \""
                        + noNamespaceSchema.toUri()
                        + "\";\n"
                        + "declare context item := validate strict { fn:doc(\""
                        + this.directory.resolve("document.xml").toUri()
                        + "\") }; 1",
                environment.applyToQuery("1"));
    }

    @Test
    public void explicitValidationUsesEnvironmentSchemasWithSkippedSourceValidation() throws Exception {
        Files.writeString(this.directory.resolve("schema.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:test"
                           xmlns:t="urn:test" elementFormDefault="qualified">
                  <xs:element name="object" type="t:abstractType"/>
                  <xs:complexType name="abstractType" abstract="true"/>
                  <xs:complexType name="concreteType"><xs:complexContent>
                    <xs:extension base="t:abstractType"><xs:sequence>
                      <xs:element name="field" type="xs:string"/>
                    </xs:sequence></xs:extension>
                  </xs:complexContent></xs:complexType>
                </xs:schema>
                """);
        Files.writeString(this.directory.resolve("document.xml"), """
                <dummy><t:object xmlns:t="urn:test" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:type="t:concreteType"><t:field>value</t:field></t:object></dummy>
                """);
        Environment environment = new Environment(
                element("""
                        <environment>
                          <schema uri="urn:test" file="schema.xsd"/>
                          <source role="." file="document.xml" validation="skip"/>
                        </environment>
                        """, "environment"), this.directory);
        String query = environment.applyToQuery("(validate { ./dummy/* })//*:field = 'value'");
        RumbleConfiguration configuration = RumbleConfiguration.builder()
                .configureSemantics(semantics -> semantics.queryLanguage("xquery31")).build();
        assertEquals(true, new Rumble(configuration).runQuery(query).getAsList().get(0).getBooleanValue());
        // The source itself stays untyped; only the explicit validate expression validates it.
        String sourceQuery = environment.applyToQuery("data(./dummy/*/*) instance of xs:untypedAtomic");
        assertEquals(true, new Rumble(configuration).runQuery(sourceQuery).getAsList().get(0).getBooleanValue());
    }

    @Test
    public void doesNotMutateASharedEnvironment() throws Exception {
        Path fallback = Files.writeString(this.directory.resolve("fallback.xq"), "fallback");
        Path replacement = Files.writeString(this.directory.resolve("replacement.xq"), "replacement");
        Environment shared = new Environment(element("<environment/>", "environment"), this.directory);
        XdmNode testCase = element(
                "<test-case><module uri=\"" + fallback.toUri() + "\" file=\"replacement.xq\"/></test-case>",
                "test-case");

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
        try (ResolvedResource resource = environment
                .getResourceResolver()
                .resolve(logicalUri, RumbleConfiguration.defaultConfiguration(), ExceptionMetadata.EMPTY_METADATA)) {
            assertEquals(expected.toUri(), resource.getSystemId());
            resource.getInputStream().readAllBytes();
        }
    }
}
