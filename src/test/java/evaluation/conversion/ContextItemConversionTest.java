package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ContextItemConversionTest {

    private final ContextItemConversion conversion = new ContextItemConversion();

    @Test
    public void convertsContextItemExpressions() {
        assertEquals("$$/doc/*", this.conversion.convert("./doc/*"));
        assertEquals("$$[1]", this.conversion.convert(".[1]"));
        assertEquals("for $x in 1 return $$", this.conversion.convert("for $x in 1 return ."));
    }

    @Test
    public void preservesOtherUsesOfDots() {
        String query = "(.., 1.5, .5, local:some.name, \"./text\", (: . :) )";
        assertEquals(query, this.conversion.convert(query));
    }
}
