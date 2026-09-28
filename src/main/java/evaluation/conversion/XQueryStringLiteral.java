package evaluation.conversion;

import org.rumbledb.compiler.utils.XmlCharRefUtils;
import org.rumbledb.exceptions.ExceptionMetadata;

/** Parses and serializes XQuery string literal source text. */
final class XQueryStringLiteral {

    private XQueryStringLiteral() {}

    static String parse(String source) {
        if (source.length() < 2) {
            // A valid XQuery string literal is always at least two characters: '' or "".
            return null;
        }

        char delimiter = source.charAt(0);
        if ((delimiter != '\'' && delimiter != '"') || source.charAt(source.length() - 1) != delimiter) {
            return null;
        }

        StringBuilder value = new StringBuilder(source.length() - 2);
        for (int i = 1; i < source.length() - 1; i++) {
            char current = source.charAt(i);
            if (current == delimiter && i + 1 < source.length() - 1 && source.charAt(i + 1) == delimiter) {
                // A doubled delimiter represents one delimiter in the literal value.
                value.append(delimiter);
                i++;
            } else {
                value.append(current);
            }
        }
        // Decode once, after collapsing doubled delimiters. The decoder does not revisit
        // its output, so &amp;#x20; stays literal &#x20; rather than becoming a space.
        return XmlCharRefUtils.unescapeXml(value.toString(), null, ExceptionMetadata.EMPTY_METADATA);
    }

    static String serialize(String value, char delimiter) {
        String delimiterString = Character.toString(delimiter);
        return delimiter
                + value.replace("&", "&amp;").replace(delimiterString, delimiterString + delimiterString)
                + delimiter;
    }
}
