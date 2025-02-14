package driver;

import net.sf.saxon.s9api.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Analytics {
    public static void main(String[] args) {
        try {
            // Initialize Saxon processor
            Processor processor = new Processor(false);
            XQueryCompiler compiler = processor.newXQueryCompiler();
            compiler.setBaseURI(Paths.get("target/surefire-reports").toUri());

            // Initialize serializer
            Serializer serializer = processor.newSerializer();
            serializer.setOutputProperty(Serializer.Property.METHOD, "json");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");

            runAggregation(compiler, serializer, "count");
            runAggregation(compiler, serializer, "fail");
            runAggregation(compiler, serializer, "error");
            runAggregation(compiler, serializer, "skip");

        } catch (SaxonApiException | IOException e) {
            e.printStackTrace();
        }
    }

    public static void runAggregation(XQueryCompiler compiler, Serializer serializer, String queryName)
            throws IOException,
                SaxonApiException {
        // Load XQuery from file
        String queryFilePath = "analytics/" + queryName + ".xquery";
        String query = new String(Files.readAllBytes(Paths.get(queryFilePath)));

        XQueryEvaluator evaluator = compiler.compile(query).load();

        // Store result in a StringBuilder
        StringBuilder output = new StringBuilder();
        serializer.setOutputWriter(new java.io.StringWriter() {
            @Override
            public void write(String str) {
                output.append(str);
            }
        });

        evaluator.run(serializer);

        // Ensure the directory exists
        Path outputPath = Paths.get("analytics_results");
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // Write the result to a JSON file
        String outputFilePath = outputPath.resolve(queryName + ".json").toString();
        try (FileWriter fileWriter = new FileWriter(outputFilePath)) {
            fileWriter.write(output.toString());
        }
        System.out.println("JSON output written to: " + outputFilePath);
    }
}
