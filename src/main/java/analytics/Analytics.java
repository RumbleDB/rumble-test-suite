package analytics;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates a single analysis JSON from one or two Surefire XML report directories.
 */
public class Analytics {
    private static final QName BASELINE_PARAM = new QName("baseline");
    private static final QName CANDIDATE_PARAM = new QName("candidate");
    private static final Path DEFAULT_INPUT_DIR = Paths.get("target", "surefire-reports");
    private static final Path QUERY_PATH = Paths.get("analytics", "main.xquery");
    private static final Path OUTPUT_DIR = Paths.get("analytics-results");
    private static final Path OUTPUT_FILE = OUTPUT_DIR.resolve("analysis.json");

    public static void main(String[] args) {
        try {
            switch (args.length) {
                case 0:
                    // Run with default input directory (target/surefire-reports)
                    run(null, DEFAULT_INPUT_DIR);
                    break;
                case 1:
                    // Run with user provided input directory (with surefire-reports)
                    run(null, Paths.get(args[0]));
                    break;
                case 2:
                    // Run with two arguments, the first as the baseline and the second as the candidate
                    run(Paths.get(args[0]), Paths.get(args[1]));
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Expected zero, one, or two arguments: [candidate-surefire-dir] or <baseline-surefire-dir> <candidate-surefire-dir>"
                    );
            }
        } catch (IOException | SaxonApiException e) {
            throw new RuntimeException("Failed to generate analysis report", e);
        }
    }

    private static void run(Path baselinePath, Path candidatePath) throws IOException, SaxonApiException {
        Processor processor = new Processor(false);
        XQueryCompiler compiler = processor.newXQueryCompiler();
        compiler.setBaseURI(QUERY_PATH.toAbsolutePath().toUri());

        String query = Files.readString(QUERY_PATH);
        XQueryEvaluator evaluator = compiler.compile(query).load();
        if (baselinePath == null) {
            evaluator.setExternalVariable(BASELINE_PARAM, XdmEmptySequence.getInstance());
        } else {
            evaluator.setExternalVariable(BASELINE_PARAM, new XdmAtomicValue(asDirectoryUri(baselinePath)));
        }
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
        System.out.println("Analysis");
        System.out.println("==================================");
        if (baselinePath != null) {
            System.out.println("Baseline: " + baselinePath.toAbsolutePath());
        }
        System.out.println("Candidate: " + candidatePath.toAbsolutePath());
        System.out.println("Output written to: " + OUTPUT_FILE.toAbsolutePath());
    }

    private static String asDirectoryUri(Path path) {
        String uri = path.toAbsolutePath().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
