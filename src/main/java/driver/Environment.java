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
    private final Map<String, String> roleLookup = new HashMap<>();
    private boolean unsupportedCollation = false;

    public Environment(XdmNode environmentNode, Path testsRepositoryDirectoryPath) {
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
        initResources(environmentNode, testsRepositoryDirectoryPath);
        initSources(environmentNode, testsRepositoryDirectoryPath);

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

    private void initResources(XdmNode environmentNode, Path testsRepositoryDirectoryPath) {
        List<XdmNode> resources = environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = testsRepositoryDirectoryPath
                .resolve(resource.attribute("file"))
                .toString();
            String uri = resource.attribute("uri");
            resourceLookup.put(uri, file);
        }
    }

    private void initSources(XdmNode environmentNode, Path testsRepositoryDirectoryPath) {
        List<XdmNode> sources = environmentNode.select(Steps.descendant("source")).asList();
        for (XdmNode source : sources) {
            String file = testsRepositoryDirectoryPath
                .resolve(source.attribute("file"))
                .toString();
            String uri = source.attribute("uri");
            String role = source.attribute("role");
            if (file != null && uri != null && !file.equals(uri)) {
                resourceLookup.put(uri, file);
            }
            if (role != null) {
                if (file == null) {
                    throw new RuntimeException("file not specified for role");
                }
                roleLookup.put(role, file);
            }
        }
    }

    public String applyToQuery(String query) {
        StringBuilder newQuery = new StringBuilder();
        for (Map.Entry<String, String> r : getRoles().entrySet()) {
            String role = r.getKey();
            String file = r.getValue();
            if (role.equals(".")) {
                newQuery.append("declare context item := doc(\"").append(file).append("\"); ");
            }
        }
        for (Map.Entry<String, String> param : getParams().entrySet()) {
            String name = param.getKey();
            String select = param.getValue();
            newQuery.append("let $").append(name).append(" := ").append(select).append(" ");
        }
        if (!getParams().isEmpty()) {
            newQuery.append("return ");
        }

        newQuery.append(query);
        String newQueryString = newQuery.toString();

        for (Map.Entry<String, String> fileLookup : getResources().entrySet()) {
            if (newQueryString.contains(fileLookup.getKey())) {
                newQueryString = newQueryString.replace(fileLookup.getKey(), fileLookup.getValue());
            }
        }
        return newQueryString;
    }

    public Map<String, String> getResources() {
        return resourceLookup;
    }

    public Map<String, String> getParams() {
        return paramLookup;
    }

    public Map<String, String> getRoles() {
        return roleLookup;
    }

    public boolean isUnsupportedCollation() {
        return unsupportedCollation;
    }
}
