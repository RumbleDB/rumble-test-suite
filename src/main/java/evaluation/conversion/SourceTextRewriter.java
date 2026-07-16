package evaluation.conversion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Applies parser-identified edits directly to the original source text. */
final class SourceTextRewriter {

    private final String source;
    private final List<SourceEdit> edits;

    SourceTextRewriter(String source) {
        this.source = source;
        this.edits = new ArrayList<>();
    }

    void replace(Token token, String replacement) {
        addEdit(token.getStartIndex(), token.getStopIndex(), replacement);
    }

    void replace(ParserRuleContext context, String replacement) {
        addEdit(context.getStart().getStartIndex(), context.getStop().getStopIndex(), replacement);
    }

    void insertBefore(Token token, String text) {
        addInsertion(token.getStartIndex(), text);
    }

    void insertAfter(Token token, String text) {
        addInsertion(token.getStopIndex() + 1, text);
    }

    String text(ParserRuleContext context) {
        int start = context.getStart().getStartIndex();
        int stop = context.getStop().getStopIndex();
        if (!isCodePointRange(start, stop)) {
            throw new IllegalArgumentException("Cannot read text for a parser-synthesized source range");
        }
        return this.source.substring(toStringOffset(start), toStringOffset(stop + 1));
    }

    String result() {
        if (this.edits.isEmpty()) {
            return this.source;
        }

        List<SourceEdit> orderedEdits = new ArrayList<>(this.edits);
        orderedEdits.sort(Comparator.comparingInt(SourceEdit::start));
        validateNonOverlapping(orderedEdits);

        StringBuilder result = new StringBuilder(this.source);
        for (int i = orderedEdits.size() - 1; i >= 0; i--) {
            SourceEdit edit = orderedEdits.get(i);
            result.replace(edit.start(), edit.endExclusive(), edit.replacement());
        }
        return result.toString();
    }

    private void addEdit(int start, int stop, String replacement) {
        if (isCodePointRange(start, stop)) {
            this.edits.add(new SourceEdit(toStringOffset(start), toStringOffset(stop + 1), replacement));
        }
    }

    private void addInsertion(int position, String text) {
        int codePointCount = this.source.codePointCount(0, this.source.length());
        if (position >= 0 && position <= codePointCount) {
            int offset = toStringOffset(position);
            this.edits.add(new SourceEdit(offset, offset, text));
        }
    }

    private boolean isCodePointRange(int start, int stop) {
        return start >= 0 && stop >= start && stop < this.source.codePointCount(0, this.source.length());
    }

    private int toStringOffset(int codePointOffset) {
        return this.source.offsetByCodePoints(0, codePointOffset);
    }

    /**
     * Validates that the given edits do not overlap. The edits must be sorted by start offset.
     */
    private static void validateNonOverlapping(List<SourceEdit> edits) {
        for (int i = 1; i < edits.size(); i++) {
            SourceEdit previous = edits.get(i - 1);
            SourceEdit current = edits.get(i);
            if (current.start() < previous.endExclusive()) {
                throw new IllegalStateException(
                        "Overlapping source edits at offsets " + previous.start() + " and " + current.start()
                );
            }
        }
    }

    private record SourceEdit(int start, int endExclusive, String replacement) {
    }
}
