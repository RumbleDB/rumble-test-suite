package evaluation.conversion;

import java.util.function.UnaryOperator;

import org.rumbledb.parser.xquery.XQueryParser;

/** Rewrites the program expression of a valid XQuery main module while preserving its prolog. */
public final class XQueryMainModuleRewriter {

    private XQueryMainModuleRewriter() {
    }

    public static String rewriteProgram(String query, UnaryOperator<String> programRewriter) {
        XQueryParser.ModuleAndThisIsItContext module = XQueryParsing.parseValidModule(query);
        if (module == null || module.module().main == null) {
            throw new IllegalArgumentException("Expected a valid XQuery main module");
        }

        XQueryParser.ProgramContext program = module.module().main.program();
        ConversionContext context = new ConversionContext(query, module);
        context.replace(program, programRewriter.apply(context.text(program)));
        return context.result();
    }
}
