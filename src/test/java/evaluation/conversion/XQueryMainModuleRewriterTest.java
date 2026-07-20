package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class XQueryMainModuleRewriterTest {

    @Test
    public void rewritesOnlyTheProgramExpression() {
        String query = "xquery version \"3.1\";\n"
            + "declare namespace ex = \"urn:semicolon;\";\n"
            + "(: keep this comment; with the prolog :)\n"
            + "declare variable $value := \"a;b\";\n"
            + "(: keep this comment before the program :)\n"
            + "$value";

        assertEquals(
            "xquery version \"3.1\";\n"
                + "declare namespace ex = \"urn:semicolon;\";\n"
                + "(: keep this comment; with the prolog :)\n"
                + "declare variable $value := \"a;b\";\n"
                + "(: keep this comment before the program :)\n"
                + "deep-equal(($value), (\"a;b\"))",
            XQueryMainModuleRewriter.rewriteProgram(
                query,
                program -> "deep-equal((" + program + "), (\"a;b\"))"
            )
        );
    }

    @Test
    public void rejectsInvalidAndLibraryModules() {
        assertThrows(
            IllegalArgumentException.class,
            () -> XQueryMainModuleRewriter.rewriteProgram("1 +", program -> program)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> XQueryMainModuleRewriter.rewriteProgram(
                "module namespace example = \"urn:example\";",
                program -> program
            )
        );
    }
}
