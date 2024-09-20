package org.yamcs.utils.parser;

import java.io.StringReader;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.yamcs.utils.parser.ast.AndExpression;
import org.yamcs.utils.parser.ast.Comparison;
import org.yamcs.utils.parser.ast.OrExpression;
import org.yamcs.utils.parser.ast.UnaryExpression;

import com.google.common.primitives.Bytes;

public abstract class Filter<T> {

    private FilterParser<T> parser;
    private AndExpression expression;

    public Filter(String query) {
        parser = new FilterParser<>(new StringReader(query));
    }

    public void parse() throws ParseException {
        expression = parser.parse();
    }

    /**
     * True if the provided field is part of the parsed query.
     * <p>
     * This method should only be used after {@link #parse()} is called.
     */
    public boolean isQueryField(String field) {
        return parser.isQueryField(field);
    }

    /**
     * True if the parsed query includes at least one text search.
     * <p>
     * This method should only be used after {@link #parse()} is called.
     */
    public boolean includesTextSearch() {
        return parser.includesTextSearch();
    }

    protected void addPrefixField(String field, BiFunction<T, String, String> resolver) {
        parser.addPrefixField(field, resolver);
    }

    protected void addStringField(String field, Function<T, String> resolver) {
        parser.addStringField(field, resolver);
    }

    protected <E extends Enum<?>> void addEnumField(String field, Class<E> enumClass, Function<T, E> resolver) {
        parser.addEnumField(field, enumClass, resolver);
    }

    protected void addNumberField(String field, Function<T, Number> resolver) {
        parser.addNumberField(field, resolver);
    }

    protected void addBooleanField(String field, Function<T, Boolean> resolver) {
        parser.addBooleanField(field, resolver);
    }

    protected void addBinaryField(String field, Function<T, byte[]> resolver) {
        parser.addBinaryField(field, resolver);
    }

    public boolean matches(T item) {
        if (expression == null) {
            return true;
        } else {
            beforeItem(item);
            return matchAndExpression(expression, item);
        }
    }

    /**
     * Called for each new item, before any comparisons.
     * <p>
     * The default implementation does nothing, concrete classes may override to hook any initialization logic.
     */
    public void beforeItem(T item) {
    }

    public String printExpression() {
        return expression.toString("  ");
    }

    /**
     * Implementatinos must search the provided item for the given literal in a manner that makes sense to the type of
     * item. Search should be exact and case-insensitive.
     * 
     * @param item
     *            Item to match
     * @param lowercaseLiteral
     *            A search string. Always lowercase.
     */
    protected abstract boolean matchesLiteral(T item, String lowercaseLiteral);

    private boolean matchOrExpression(OrExpression expression, T item) {
        for (UnaryExpression clause : expression.getClauses()) {
            if (matchUnaryExpression(clause, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchAndExpression(AndExpression expression, T item) {
        for (OrExpression clause : expression.getClauses()) {
            if (!matchOrExpression(clause, item)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchUnaryExpression(UnaryExpression expression, T item) {
        boolean res;
        if (expression.getComparison() != null) {
            res = matchComparison(expression.getComparison(), item);
        } else {
            res = matchAndExpression(expression.getAndExpression(), item);
        }
        return expression.isNot() ? !res : res;
    }

    private boolean matchComparison(Comparison comparison, T item) {
        if (comparison.comparator == null) {
            return matchesLiteral(item, comparison.comparable);
        }

        var stringResolver = parser.getStringResolver(comparison.comparable);
        if (stringResolver != null) {
            var fieldValue = stringResolver.apply(item);
            return matchStringComparison(comparison, fieldValue);
        }

        var enumResolver = parser.getEnumResolver(comparison.comparable);
        if (enumResolver != null) {
            return matchEnumComparison(comparison, item, enumResolver);
        }

        var numberResolver = parser.getNumberResolver(comparison.comparable);
        if (numberResolver != null) {
            return matchNumberComparison(comparison, item, numberResolver);
        }

        var booleanResolver = parser.getBooleanResolver(comparison.comparable);
        if (booleanResolver != null) {
            return matchBooleanComparison(comparison, item, booleanResolver);
        }

        var binaryResolver = parser.getBinaryResolver(comparison.comparable);
        if (binaryResolver != null) {
            return matchBinaryComparison(comparison, item, binaryResolver);
        }

        var prefixResolver = parser.getPrefixResolver(comparison.comparable);
        if (prefixResolver != null) {
            var fieldValue = prefixResolver.apply(item, comparison.comparable);
            return matchStringComparison(comparison, fieldValue);
        }

        throw new IllegalArgumentException("Unexpected field '" + comparison.comparable + "'");
    }

    private boolean matchStringComparison(Comparison comparison, String fieldValue) {
        switch (comparison.comparator) {
        case EQUAL_TO:
            return isEqual(fieldValue, comparison.value); // Faster than compare
        case NOT_EQUAL_TO:
            return !isEqual(fieldValue, comparison.value); // Faster than compare
        case GREATER_THAN:
            return compareStringField(fieldValue, comparison.value) > 0;
        case GREATER_THAN_OR_EQUAL_TO:
            return compareStringField(fieldValue, comparison.value) >= 0;
        case LESS_THAN:
            return compareStringField(fieldValue, comparison.value) < 0;
        case LESS_THAN_OR_EQUAL_TO:
            return compareStringField(fieldValue, comparison.value) <= 0;
        case HAS:
            return testStringFieldContains(fieldValue, comparison.value);
        case RE_EQUAL_TO:
            return testRegexMatch(fieldValue, comparison.pattern);
        case RE_NOT_EQUAL_TO:
            return !testRegexMatch(fieldValue, comparison.pattern);
        default:
            throw new IllegalStateException("Unexpected comparator " + comparison.comparator);
        }
    }

    private boolean matchEnumComparison(Comparison comparison, T item, Function<T, ? extends Enum<?>> resolver) {
        Class<? extends Enum<?>> enumClass = parser.getEnumClass(comparison.comparable);
        Enum<?> comparand = null;
        if (enumClass != null) {
            comparand = parser.findEnum(enumClass, comparison.value);
        }

        var fieldValue = resolver.apply(item);
        switch (comparison.comparator) {
        case EQUAL_TO:
        case HAS:
        case RE_EQUAL_TO:
            return compareEnumField(fieldValue, comparand) == 0;
        case NOT_EQUAL_TO:
        case RE_NOT_EQUAL_TO:
            return compareEnumField(fieldValue, comparand) != 0;
        case GREATER_THAN:
            return compareEnumField(fieldValue, comparand) > 0;
        case GREATER_THAN_OR_EQUAL_TO:
            return compareEnumField(fieldValue, comparand) >= 0;
        case LESS_THAN:
            return compareEnumField(fieldValue, comparand) < 0;
        case LESS_THAN_OR_EQUAL_TO:
            return compareEnumField(fieldValue, comparand) <= 0;
        default:
            throw new IllegalStateException("Unexpected comparator " + comparison.comparator);
        }
    }

    private boolean matchNumberComparison(Comparison comparison, T item, Function<T, Number> resolver) {
        var fieldValue = resolver.apply(item);
        var comparand = comparison.value.equalsIgnoreCase("null")
                ? null
                : Double.parseDouble(comparison.value);

        switch (comparison.comparator) {
        case EQUAL_TO:
        case HAS:
        case RE_EQUAL_TO:
            return compareNumberField(fieldValue, comparand) == 0;
        case NOT_EQUAL_TO:
        case RE_NOT_EQUAL_TO:
            return compareNumberField(fieldValue, comparand) != 0;
        case GREATER_THAN:
            return compareNumberField(fieldValue, comparand) > 0;
        case GREATER_THAN_OR_EQUAL_TO:
            return compareNumberField(fieldValue, comparand) >= 0;
        case LESS_THAN:
            return compareNumberField(fieldValue, comparand) < 0;
        case LESS_THAN_OR_EQUAL_TO:
            return compareNumberField(fieldValue, comparand) <= 0;
        default:
            throw new IllegalStateException("Unexpected comparator " + comparison.comparator);
        }
    }

    private boolean matchBooleanComparison(Comparison comparison, T item, Function<T, Boolean> resolver) {
        var fieldValue = resolver.apply(item);
        var comparand = comparison.value.equalsIgnoreCase("null")
                ? null
                : Boolean.parseBoolean(comparison.value);

        switch (comparison.comparator) {
        case EQUAL_TO:
        case HAS:
        case RE_EQUAL_TO:
            return compareBooleanField(fieldValue, comparand) == 0;
        case NOT_EQUAL_TO:
        case RE_NOT_EQUAL_TO:
            return compareBooleanField(fieldValue, comparand) != 0;
        case GREATER_THAN:
            return compareBooleanField(fieldValue, comparand) > 0;
        case GREATER_THAN_OR_EQUAL_TO:
            return compareBooleanField(fieldValue, comparand) >= 0;
        case LESS_THAN:
            return compareBooleanField(fieldValue, comparand) < 0;
        case LESS_THAN_OR_EQUAL_TO:
            return compareBooleanField(fieldValue, comparand) <= 0;
        default:
            throw new IllegalStateException("Unexpected comparator " + comparison.comparator);
        }
    }

    private boolean matchBinaryComparison(Comparison comparison, T item, Function<T, byte[]> resolver) {
        var fieldValue = resolver.apply(item);
        var comparand = comparison.binary;

        switch (comparison.comparator) {
        case EQUAL_TO:
        case RE_EQUAL_TO:
            return Arrays.equals(fieldValue, comparand);
        case HAS:
            return Bytes.indexOf(fieldValue, comparand) != -1;
        case NOT_EQUAL_TO:
        case RE_NOT_EQUAL_TO:
            return !Arrays.equals(fieldValue, comparand);
        case GREATER_THAN:
            return compareBinaryField(fieldValue, comparand) > 0;
        case GREATER_THAN_OR_EQUAL_TO:
            return compareBinaryField(fieldValue, comparand) >= 0;
        case LESS_THAN:
            return compareBinaryField(fieldValue, comparand) < 0;
        case LESS_THAN_OR_EQUAL_TO:
            return compareBinaryField(fieldValue, comparand) <= 0;
        default:
            throw new IllegalStateException("Unexpected comparator " + comparison.comparator);
        }
    }

    private boolean isEqual(String fieldValue, String comparand) {
        if (fieldValue == null) {
            return comparand.equalsIgnoreCase("null");
        }
        return fieldValue.equalsIgnoreCase(comparand);
    }

    private boolean testRegexMatch(String fieldValue, Pattern comparand) {
        if (fieldValue == null) {
            return false;
        }
        // Unanchored regex
        return comparand.matcher(fieldValue).find();
    }

    private int compareStringField(String fieldValue, String comparand) {
        if (fieldValue == null) {
            return -1;
        }
        return fieldValue.compareToIgnoreCase(comparand);
    }

    private boolean testStringFieldContains(String fieldValue, String comparand) {
        if (fieldValue == null) {
            return false;
        }
        return fieldValue.toLowerCase().contains(comparand);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compareEnumField(Enum fieldValue, Enum comparand) {
        // Nulls first
        if (fieldValue == null) {
            return comparand == null ? 0 : -1;
        } else if (comparand == null) {
            return 1;
        }
        return fieldValue.compareTo(comparand);
    }

    private int compareNumberField(Number fieldValue, Double comparand) {
        // Nulls first
        if (fieldValue == null) {
            return comparand == null ? 0 : -1;
        } else if (comparand == null) {
            return 1;
        }

        if (fieldValue instanceof Integer i) {
            return Double.compare(i, comparand);
        } else if (fieldValue instanceof Long l) {
            return Double.compare(l, comparand);
        } else if (fieldValue instanceof Double d) {
            return Double.compare(d, comparand);
        } else if (fieldValue instanceof Float f) {
            return Double.compare(f, comparand);
        } else if (fieldValue instanceof Short s) {
            return Double.compare(s, comparand);
        } else if (fieldValue instanceof Byte b) {
            return Double.compare(b, comparand);
        } else {
            throw new IllegalArgumentException("Unexpected number class");
        }
    }

    private int compareBooleanField(Boolean fieldValue, Boolean comparand) {
        // Nulls first
        if (fieldValue == null) {
            return comparand == null ? 0 : -1;
        } else if (comparand == null) {
            return 1;
        }
        return fieldValue.compareTo(comparand);
    }

    private int compareBinaryField(byte[] fieldValue, byte[] comparand) {
        // Nulls first
        if (fieldValue == null) {
            return comparand == null ? 0 : -1;
        } else if (comparand == null) {
            return 1;
        }
        return Arrays.compare(fieldValue, comparand);
    }
}
