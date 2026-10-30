package iq.base;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import evaluation.Environment;
import evaluation.conversion.Converter;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;

import org.rumbledb.api.ExternalBindings;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.CompilationConfiguration;
import org.rumbledb.config.RumbleConfiguration;
import org.rumbledb.exceptions.RumbleException;
import org.rumbledb.resources.ResourceResolver;

/**
 * Class to keep the test case string, execution environment, and a cached primary result
 * The result must be cached, because some test cases are not deterministic. They are usually held inside an any-of
 * assertion,
 * and we need to make sure that we are comparing always with the same result instead of re-evaluating every time
 */
class AssertionContext {
    private final String testString;
    private final Environment environment;
    private final boolean useXQueryParser;
    private final RumbleConfiguration rumbleConfig;
    private final String xmlVersion;
    private final String xsdVersion;
    private final String defaultFormattingLanguage;
    private final boolean staticTyping;
    private final String staticBaseUri;
    private QueryEvaluation primaryEvaluation;

    AssertionContext(
            String testString,
            Environment environment,
            boolean useXQueryParser,
            RumbleConfiguration rumbleConfig,
            String xmlVersion,
            String xsdVersion,
            String defaultFormattingLanguage,
            boolean staticTyping,
            String staticBaseUri) {
        /// This is the test
        this.testString = testString;
        this.environment = environment;
        this.useXQueryParser = useXQueryParser;
        this.rumbleConfig = rumbleConfig;
        this.xmlVersion = xmlVersion;
        this.xsdVersion = xsdVersion;
        this.defaultFormattingLanguage = defaultFormattingLanguage;
        this.staticTyping = staticTyping;
        this.staticBaseUri = staticBaseUri;
    }

    String getTestString() {
        return this.testString;
    }

    List<Item> getPrimaryResult() {
        return getPrimaryEvaluation().getResult();
    }

    QueryEvaluation getPrimaryEvaluation() {
        if (this.primaryEvaluation == null) {
            this.primaryEvaluation = evaluateQuery(this.testString);
        }
        return this.primaryEvaluation;
    }

    List<Item> runQuery(String query) {
        return evaluateQuery(query).getResult();
    }

    /** Assertions have their own static context and see the cached result as an external variable. */
    List<Item> runAssertion(String expression, XdmNode assertion) {
        Map<String, String> namespaceBindings = new LinkedHashMap<>();
        if (this.environment != null) namespaceBindings.putAll(this.environment.getNamespaceBindings());
        var namespaces = assertion.axisIterator(Axis.NAMESPACE);
        while (namespaces.hasNext()) {
            XdmNode namespace = namespaces.next();
            if (namespace.getNodeName() == null) continue;
            String prefix = namespace.getNodeName().getLocalName();
            if (prefix.isEmpty() || "xml".equals(prefix)) continue;
            // An assertion-local prefix binding overrides its environment binding.
            namespaceBindings.put(prefix, namespace.getStringValue());
        }
        List<Item> result = getPrimaryResult();
        StringBuilder query = new StringBuilder();
        for (var binding : namespaceBindings.entrySet()) {
            if ("xml".equals(binding.getKey())) continue;
            if (binding.getKey().isEmpty()) {
                query.append("declare default element namespace ");
            } else {
                query.append("declare namespace ").append(binding.getKey()).append(" = ");
            }
            query.append("\"")
                    .append(binding.getValue().replace("&", "&amp;").replace("\"", "\"\""))
                    .append("\";\n");
        }
        // QT3 supplies a context item only when the test result is a singleton.
        if (result.size() == 1) query.append("declare context item external;\n");
        query.append("declare variable $result external;\n").append(expression);
        String source = query.toString();
        if (!this.useXQueryParser) source = Converter.convert(source);
        RumbleConfiguration config = applyDependenciesToConfig(this.rumbleConfig);
        ResourceResolver resolver =
                this.environment == null ? new ResourceResolver() : this.environment.getResourceResolver(config);
        ExternalBindings bindings = new ExternalBindings();
        bindings.bindItems("result", result);
        if (result.size() == 1) bindings.bindItems("$", result);
        return new Rumble(new CompilationConfiguration(config, resolver))
                .runQuery(source, bindings)
                .getAsList();
    }

    String getPrimarySerialization() {
        return getPrimaryEvaluation().getSerializedResult();
    }

    private QueryEvaluation evaluateQuery(String query) {
        try {
            SequenceOfItems result = executeQuery(query);
            result.getAsList();
            return QueryEvaluation.withResult(result);
        } catch (RumbleException e) {
            return QueryEvaluation.withError(e);
        }
    }

    private SequenceOfItems executeQuery(String query) {
        if (this.environment != null) {
            query = this.environment.applyToQuery(query);
        }

        if (!this.useXQueryParser) {
            query = Converter.convert(query);
        }

        System.out.println("=== Translated query ===");
        System.out.println(query);
        System.out.println("=== End translated query ===");

        RumbleConfiguration updatedConfig = applyDependenciesToConfig(this.rumbleConfig);
        ResourceResolver resourceResolver =
                this.environment == null ? new ResourceResolver() : this.environment.getResourceResolver(updatedConfig);
        CompilationConfiguration compilationConfig = new CompilationConfiguration(updatedConfig, resourceResolver);
        return new Rumble(compilationConfig).runQuery(query);
    }

    private RumbleConfiguration applyDependenciesToConfig(RumbleConfiguration config) {
        RumbleConfiguration.RumbleConfigurationBuilder builder = config.toBuilder();

        builder.configureSemantics(
                s -> s.xmlVersion("1.0").xsdVersion(this.xsdVersion).allowScripting(false));

        String v = this.xmlVersion;
        if (v != null) {
            v = v.trim();
        }

        if ("1.1".equals(v)) {
            builder.configureSemantics(s -> s.xmlVersion("1.1"));
        } else if ("1.0".equals(v)) {
            builder.configureSemantics(s -> s.xmlVersion("1.0").allowScripting(false));
        }

        if (this.defaultFormattingLanguage != null) {
            builder.configureFormatting(f -> f.defaultFormattingLanguage(this.defaultFormattingLanguage));
        }

        if (this.staticTyping) {
            builder.configureAnalysis(a -> a.enableStaticTyping(true));
        }

        if (this.staticBaseUri != null) {
            builder.configureSemantics(s -> s.staticBaseUri(this.staticBaseUri));
        }
        return builder.build();
    }
}
