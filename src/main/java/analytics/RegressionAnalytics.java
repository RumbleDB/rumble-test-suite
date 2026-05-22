package analytics;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compares two Surefire XML report directories and writes a machine-readable regression report.
 */
public class RegressionAnalytics {
    private static final QName BASELINE_PARAM = new QName("baseline");
    private static final QName CANDIDATE_PARAM = new QName("candidate");
    private static final Path QUERY_PATH = Paths.get("analytics", "regression", "regressions.xquery");
    private static final Path OUTPUT_DIR = Paths.get("analytics-regressions");
    private static final Path OUTPUT_FILE = OUTPUT_DIR.resolve("regressions.json");

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Expected exactly two arguments: <baseline-surefire-dir> <candidate-surefire-dir>"
            );
        }

        try {
            run(Paths.get(args[0]), Paths.get(args[1]));
        } catch (IOException | SaxonApiException e) {
            throw new RuntimeException("Failed to generate regression report", e);
        }
    }

    private static void run(Path baselinePath, Path candidatePath) throws IOException, SaxonApiException {
        Processor processor = new Processor(false);
        XQueryCompiler compiler = processor.newXQueryCompiler();
        compiler.setBaseURI(Paths.get(".").toUri());

        String query = Files.readString(QUERY_PATH);
        XQueryEvaluator evaluator = compiler.compile(query).load();
        evaluator.setExternalVariable(BASELINE_PARAM, new XdmAtomicValue(asDirectoryUri(baselinePath)));
        evaluator.setExternalVariable(CANDIDATE_PARAM, new XdmAtomicValue(asDirectoryUri(candidatePath)));

        Files.createDirectories(OUTPUT_DIR);
        try (FileWriter fileWriter = new FileWriter(OUTPUT_FILE.toFile())) {
            Serializer serializer = processor.newSerializer();
            serializer.setOutputWriter(fileWriter);
            serializer.setOutputProperty(Serializer.Property.METHOD, "json");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            evaluator.run(serializer);
        }

        System.out.println("==================================");
        System.out.println("Regression analysis");
        System.out.println("==================================");
        System.out.println("Baseline: " + baselinePath.toAbsolutePath());
        System.out.println("Candidate: " + candidatePath.toAbsolutePath());
        System.out.println("Output written to: " + OUTPUT_FILE.toAbsolutePath());
    }

    private static String asDirectoryUri(Path path) {
        String uri = path.toAbsolutePath().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
