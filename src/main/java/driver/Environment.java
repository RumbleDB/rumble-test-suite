package driver;

import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.streams.Steps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

public class Environment {
    private final Map<String, String> resourceLookup = new HashMap<>();
    private final Map<String, String> sourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();

    public Environment(XdmNode environmentNode) {
        initParams(environmentNode);
    }

    public Environment(XdmNode environmentNode, Path testsRepositoryDirectoryPath, String testSet) {
        this(environmentNode);
        initResources(environmentNode, testsRepositoryDirectoryPath, testSet);

    }

    private void initParams(XdmNode environmentNode) {
        for (XdmNode param : environmentNode.children("param")) {
            String name = param.attribute("name");
            String select = param.attribute("select");
            paramLookup.put(name, select);
        }
    }

    private void initResources(XdmNode environmentNode, Path testsRepositoryDirectoryPath, String testSet) {
        List<XdmNode> resources = environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = testsRepositoryDirectoryPath.resolve(testSet)
                .resolve(resource.attribute("file"))
                .toString();
            String uri = resource.attribute("uri");
            resourceLookup.put(uri, file);
        }
    }

    public Map<String, String> getResources() {
        return resourceLookup;
    }

    public Map<String, String> getParams() {
        return paramLookup;
    }
}
