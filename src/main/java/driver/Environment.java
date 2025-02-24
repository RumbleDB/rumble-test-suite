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

    public Environment(XdmNode environmentNode, Path envPath) {
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
        initResources(environmentNode, envPath);
        initSources(environmentNode, envPath);

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

    private void initResources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> resources = environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = envPath
                .resolve(resource.attribute("file"))
                .toString();
            String uri = resource.attribute("uri");
            resourceLookup.put(uri, file);
        }
    }

    private void initSources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> sources = environmentNode.select(Steps.descendant("source")).asList();
        for (XdmNode source : sources) {
            String file = envPath
                .resolve(source.attribute("file"))
                .toString();
            String uri = source.attribute("uri");
            String role = source.attribute("role");
            if (uri != null && !file.equals(uri)) {
                resourceLookup.put(uri, file);
            } else if (role != null) {
                roleLookup.put(role, file);
            }
        }
    }

    /**
     * This method takes a query and modifies it such that it executes inside the environment. It adds a context-item
     * declaration, variable declarations and replaces URIs with the right filepaths.
     * 
     * @param query contains the query that wants to be executed.
     * @return a String containing the updated query with the context-item, params and resources set.
     */
    public String applyToQuery(String query) {
        StringBuilder newQuery = new StringBuilder();
        for (Map.Entry<String, String> r : roleLookup.entrySet()) {
            String role = r.getKey();
            String file = r.getValue();
            if (role.equals(".")) {
                newQuery.append("declare context item := doc(\"").append(file).append("\"); ");
            } else {
                newQuery.append("declare variable ").append(role).append(" := doc(").append(file).append(");");
            }
        }
        for (Map.Entry<String, String> param : paramLookup.entrySet()) {
            String name = param.getKey();
            String select = param.getValue();
            newQuery.append("declare variable $").append(name).append(" := ").append(select).append(";");
        }

        newQuery.append(query);
        String newQueryString = newQuery.toString();

        for (Map.Entry<String, String> fileLookup : resourceLookup.entrySet()) {
            if (newQueryString.contains(fileLookup.getKey())) {
                newQueryString = newQueryString.replace(fileLookup.getKey(), fileLookup.getValue());
            }
        }
        return newQueryString;
    }

    public boolean isUnsupportedCollation() {
        return unsupportedCollation;
    }
}
