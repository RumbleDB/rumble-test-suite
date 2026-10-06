package evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The QT3 dependencies of a test case that the current implementation or configuration does not satisfy.
 *
 * <p>
 * The skip message is parsed by the analytics (see {@code analytics/modules/skips.xquery}), so its format must stay
 * stable: {@code <category>: <dependency>; <dependency>...}, where each dependency is written as
 * {@code <type>=[!]<value> [<category>]} and {@code !} marks a dependency with {@code satisfied="false"}.
 */
public record SkipReason(List<UnmetDependency> dependencies) {
    /**
     * Why a dependency prevents a test case from running. Declared in increasing precedence: a test case takes the
     * category of its highest-precedence dependency, because implementing a missing feature does not make a test
     * case runnable while another dependency still rules it out.
     */
    public enum Category {
        /** The test case requires something that is not implemented yet. */
        MISSING_FEATURE("missing-feature"),
        /** The test case requires the absence of a supported feature, or an implementation-defined alternative. */
        NOT_APPLICABLE("not-applicable"),
        /** The test case only applies to another language or specification version. */
        OTHER_SPEC("other-spec");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public record UnmetDependency(String type, String value, boolean satisfied, Category category) {
        String describe() {
            return this.type + "=" + (this.satisfied ? "" : "!") + this.value + " [" + this.category.label() + "]";
        }
    }

    public SkipReason {
        if (dependencies.isEmpty()) {
            throw new IllegalArgumentException("A skip reason needs at least one unmet dependency");
        }
        dependencies = List.copyOf(dependencies);
    }

    public Category category() {
        return this.dependencies.stream()
                .map(UnmetDependency::category)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    public String message() {
        return this.category().label()
                + ": "
                + this.dependencies.stream().map(UnmetDependency::describe).collect(Collectors.joining("; "));
    }

    @Override
    public String toString() {
        return message();
    }
}
