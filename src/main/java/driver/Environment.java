package driver;

import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.streams.Steps;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

public class Environment {
    private final Map<String, String> resourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();
    private boolean unsupportedCollation = false;

    public Environment(XdmNode environmentNode) {
        initParams(environmentNode);
        Iterator<XdmNode> collation = environmentNode.children("collation").iterator();
        if (
            collation.hasNext()
                && !collation.next()
                    .attribute("uri")
                    .equals("http://www.w3.org/2005/xpath-functions/collation/codepoint")
        ) {
            unsupportedCollation = true;
        }
    }

    public Environment(XdmNode environmentNode, Path testsRepositoryDirectoryPath, String testSet) {
        this(environmentNode);
        initResources(environmentNode, testsRepositoryDirectoryPath, testSet);

    }

    private void initParams(XdmNode environmentNode) {
        for (XdmNode param : environmentNode.children("param")) {
            String name = param.attribute("name");
            String select = param.attribute("select");
            String type = param.attribute("as"); // TODO: implement if needed
            String declared = param.attribute("declared"); // TODO: implement if needed
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

    public boolean isUnsupportedCollation() {
        return unsupportedCollation;
    }
}
