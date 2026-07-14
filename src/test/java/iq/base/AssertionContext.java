package iq.base;

import evaluation.Environment;
import evaluation.conversion.Converter;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.exceptions.RumbleException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private final String xmlVersion;
    private final String defaultFormattingLanguage;
    private final boolean staticTyping;
    private final String staticBaseUri;
    private QueryEvaluation primaryEvaluation;

    AssertionContext(
            String testString,
            Environment environment,
            boolean useXQueryParser,
            String xmlVersion,
            String defaultFormattingLanguage,
            boolean staticTyping,
            String staticBaseUri
    ) {
        /// This is the test
        this.testString = testString;
        this.environment = environment;
        this.useXQueryParser = useXQueryParser;
        this.xmlVersion = xmlVersion;
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

    String getPrimarySerialization() {
        return getPrimaryEvaluation().getSerializedResult();
    }

    private QueryEvaluation evaluateQuery(String query) {
        try {
            return QueryEvaluation.withResult(executeQuery(query));
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

        RumbleRuntimeConfiguration rumbleConfig = createRumbleConfig();
        applyDependenciesToConfig(rumbleConfig);
        return new Rumble(rumbleConfig).runQuery(query);
    }

    private RumbleRuntimeConfiguration createRumbleConfig() {
        List<String> arguments = new ArrayList<>(
            Arrays.asList(
                "--output-format",
                "json",
                "--materialization-cap",
                "1000000000",
                "--default-language",
                this.useXQueryParser ? "xquery31" : "jsoniq40"
            )
        );
        if (this.staticTyping) {
            arguments.add("--static-typing");
            arguments.add("yes");
        }
        return new RumbleRuntimeConfiguration(arguments.toArray(new String[0]));
    }

    private void applyDependenciesToConfig(RumbleRuntimeConfiguration rumbleConfig) {
        rumbleConfig.setXmlVersion("1.0");

        String v = this.xmlVersion;
        if (v != null) {
            v = v.trim();
        }

        if ("1.1".equals(v)) {
            rumbleConfig.setXmlVersion("1.1");
        } else if ("1.0".equals(v)) {
            rumbleConfig.setXmlVersion("1.0");
        }

        if (this.defaultFormattingLanguage != null) {
            rumbleConfig.setDefaultFormattingLanguage(this.defaultFormattingLanguage);
        }

        if (this.staticBaseUri != null) {
            rumbleConfig.setStaticBaseUri(this.staticBaseUri);
        }
    }
}
