package iq.base;

import evaluation.Environment;
import evaluation.conversion.Converter;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleConfiguration;
import org.rumbledb.exceptions.RumbleException;

import java.util.ArrayList;
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
    private final RumbleConfiguration rumbleConfig;
    private final String xmlVersion;
    private final String defaultFormattingLanguage;
    private QueryEvaluation primaryEvaluation;

    AssertionContext(
            String testString,
            Environment environment,
            boolean useXQueryParser,
            RumbleConfiguration rumbleConfig,
            String xmlVersion,
            String defaultFormattingLanguage
    ) {
        /// This is the test
        this.testString = testString;
        this.environment = environment;
        this.useXQueryParser = useXQueryParser;
        this.rumbleConfig = rumbleConfig;
        this.xmlVersion = xmlVersion;
        this.defaultFormattingLanguage = defaultFormattingLanguage;
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

    private QueryEvaluation evaluateQuery(String query) {
        try {
            return QueryEvaluation.withResult(executeQuery(query));
        } catch (RumbleException e) {
            return QueryEvaluation.withError(e);
        }
    }

    private List<Item> executeQuery(String query) {
        if (this.environment != null) {
            query = this.environment.applyToQuery(query);
        }

        if (!this.useXQueryParser) {
            query = Converter.convert(query);
        }

        RumbleConfiguration updatedConfig = applyDependenciesToConfig(this.rumbleConfig);
        SequenceOfItems queryResult = new Rumble(updatedConfig).runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateList(resultAsList, 0);
        return resultAsList;
    }

    private RumbleConfiguration applyDependenciesToConfig(RumbleConfiguration config) {
        RumbleConfiguration.RumbleConfigurationBuilder builder = config.toBuilder();

        builder.configureSemantics(s -> s.xmlVersion("1.0"));

        String v = this.xmlVersion;
        if (v != null) {
            v = v.trim();
        }

        if ("1.1".equals(v)) {
            builder.configureSemantics(s -> s.xmlVersion("1.1"));
        } else if ("1.0".equals(v)) {
            builder.configureSemantics(s -> s.xmlVersion("1.0"));
        }

        if (this.defaultFormattingLanguage != null) {
            builder.configureFormatting(f -> f.defaultFormattingLanguage(this.defaultFormattingLanguage));
        }
        return builder.build();
    }
}

