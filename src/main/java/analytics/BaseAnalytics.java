package analytics;

import net.sf.saxon.s9api.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for analytics processing that provides common functionality for both pre-aggregation and final
 * aggregation.
 */
public abstract class BaseAnalytics {
    protected final Processor processor;
    protected final XQueryCompiler compiler;
    protected final String inputDir;
    protected final String outputDir;
    protected final String querySubDir;
    protected final String scope;

    protected BaseAnalytics(String inputDir, String outputDir, String querySubDir, String scope) {
        this.processor = new Processor(false);
        this.compiler = processor.newXQueryCompiler();
        this.compiler.setBaseURI(Paths.get(inputDir).toUri());
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.querySubDir = querySubDir;
        this.scope = scope;
    }

    public void run() {
        try {
            // Run queries
            runQuery("fail");
            runQuery("error");
            runQuery("skip");
            runQuery("count");
        } catch (SaxonApiException | IOException e) {
            e.printStackTrace();
        }
    }

    private void runQuery(String queryName) throws IOException, SaxonApiException {
        Path queryFilePath = Paths.get("analytics", querySubDir, queryName + ".xquery");
        String query = new String(Files.readAllBytes(Paths.get(queryFilePath.toString())));

        // Compile and evaluate query
        XQueryEvaluator evaluator = compiler.compile(query).load();

        // Prepare output directory
        Path outputPath = Paths.get(this.outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        Path outputFilePath = outputPath.resolve((scope.equals("") ? "" : scope + ".") + queryName + ".json");

        // Create a new serializer and stream directly to file
        try (FileWriter fileWriter = new FileWriter(outputFilePath.toFile())) {
            Serializer serializer = processor.newSerializer();
            serializer.setOutputWriter(fileWriter);
            serializer.setOutputProperty(Serializer.Property.METHOD, "json");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");

            evaluator.run(serializer);
        }

        // Logging
        System.out.println("==================================");
        System.out.println(getLogPrefix() + ": " + queryName);
        System.out.println("==================================");
        System.out.println("Output written to: " + outputFilePath.toAbsolutePath());
    }

    protected abstract String getLogPrefix();
}
