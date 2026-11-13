package org.yamcs.activities.triggers;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.mdb.ConditionParser;
import org.yamcs.mdb.Mdb;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.BooleanExpression;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.util.DataTypeUtil;
import org.yamcs.xtce.util.NameReference;

/**
 * Parses a boolean expression over parameters into an XTCE {@link BooleanExpression}.
 * <p>
 * The syntax is similar to the Packet Viewer filter:
 *
 * <pre>
 * /YSS/SIMULATOR/BatteryVoltage2 &lt; 10 &amp;&amp; (/YSS/SIMULATOR/Mode == "SAFE" OR NOT /YSS/SIMULATOR/Flag == true)
 * </pre>
 *
 * <ul>
 * <li>Logical operators: {@code AND} / {@code &&}, {@code OR} / {@code ||}, {@code NOT} / {@code !}, and
 * parentheses. {@code AND} binds stronger than {@code OR}.
 * <li>Comparison operators: {@code ==} / {@code =} / {@code EQ}, {@code !=} / {@code NE}, {@code <} / {@code LT},
 * {@code <=} / {@code LE}, {@code >} / {@code GT}, {@code >=} / {@code GE}.
 * <li>The left side of a comparison is a qualified parameter name, optionally followed by an aggregate member path
 * ({@code /a/b.member[2]}) and/or the suffix {@code .raw} to compare the raw value.
 * <li>The right side is a literal (quoted or not), {@code true}/{@code false}, or a reference to another parameter
 * prefixed with {@code $}.
 * </ul>
 * <p>
 * Expressions in the spreadsheet syntax ({@code &(a < 1; b == "X")}) are also accepted.
 */
public class ConditionExpressionParser {

    private final Mdb mdb;

    // Parameters in the order they are referenced
    private final Set<Parameter> parameters = new LinkedHashSet<>();

    private List<Token> tokens;
    private int pos;

    public ConditionExpressionParser(Mdb mdb) {
        this.mdb = mdb;
    }

    /**
     * Parses the expression.
     *
     * @throws ParseException
     *             if the expression is invalid, or refers to unknown parameters.
     */
    public BooleanExpression parse(String expression) throws ParseException {
        parameters.clear();
        if (expression == null || expression.isBlank()) {
            throw new ParseException("Empty expression", 0);
        }

        var trimmed = expression.trim();
        if ((trimmed.startsWith("&(") || trimmed.startsWith("|(")) && trimmed.endsWith(")")) {
            return parseSpreadsheetSyntax(trimmed);
        }

        tokens = tokenize(trimmed);
        pos = 0;
        var result = parseOr();
        if (pos < tokens.size()) {
            var token = tokens.get(pos);
            throw new ParseException("Unexpected '" + token.text + "'", token.offset);
        }
        return result;
    }

    /**
     * Parameters referenced by the last parsed expression, in order of appearance.
     */
    public Set<Parameter> getParameters() {
        return parameters;
    }

    private BooleanExpression parseSpreadsheetSyntax(String expression) throws ParseException {
        var unresolved = new ArrayList<String>();
        var parser = new ConditionParser(name -> {
            var nr = new NameReference(name, NameReference.Type.PARAMETER);
            var p = mdb.getParameter(name);
            if (p != null) {
                nr.resolved(p);
                parameters.add(p);
            } else {
                unresolved.add(name);
            }
            return nr;
        });
        BooleanExpression result;
        try {
            result = parser.parseBooleanExpression(expression);
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage(), 0);
        }
        if (!unresolved.isEmpty()) {
            throw new ParseException("Unknown parameter '" + unresolved.get(0) + "'", 0);
        }
        return result;
    }

    private BooleanExpression parseOr() throws ParseException {
        var first = parseAnd();
        if (!peek(TokenType.OR)) {
            return first;
        }
        var or = new ORedConditions();
        or.addConditionExpression(first);
        while (accept(TokenType.OR)) {
            or.addConditionExpression(parseAnd());
        }
        return or;
    }

    private BooleanExpression parseAnd() throws ParseException {
        var first = parseUnary();
        if (!peek(TokenType.AND)) {
            return first;
        }
        var and = new ANDedConditions();
        and.addConditionExpression(first);
        while (accept(TokenType.AND)) {
            and.addConditionExpression(parseUnary());
        }
        return and;
    }

    private BooleanExpression parseUnary() throws ParseException {
        if (accept(TokenType.NOT)) {
            return negate(parseUnary());
        } else if (accept(TokenType.LPAREN)) {
            var expr = parseOr();
            expect(TokenType.RPAREN, "')'");
            return expr;
        } else {
            return parseComparison();
        }
    }

    private BooleanExpression parseComparison() throws ParseException {
        var refToken = expect(TokenType.ATOM, "parameter name");
        var leftRef = resolveReference(refToken);

        var opToken = expect(TokenType.OPERATOR, "comparison operator");
        var operator = toOperator(opToken);

        var valueToken = next("value");
        Condition condition;
        if (valueToken.type == TokenType.ATOM && valueToken.text.startsWith("$")) {
            var rightRef = resolveReference(new Token(TokenType.ATOM,
                    valueToken.text.substring(1), valueToken.offset + 1));
            condition = new Condition(operator, leftRef, rightRef);
        } else if (valueToken.type == TokenType.ATOM || valueToken.type == TokenType.QUOTED) {
            var value = valueToken.text;
            if (valueToken.type == TokenType.ATOM) {
                if ("true".equalsIgnoreCase(value)) {
                    value = BooleanDataType.DEFAULT_ONE_STRING_VALUE;
                } else if ("false".equalsIgnoreCase(value)) {
                    value = BooleanDataType.DEFAULT_ZERO_STRING_VALUE;
                }
            }
            condition = new Condition(operator, leftRef, value);
            try {
                // Like Condition.validateValueType, but taking the member path into account
                var dtype = leftRef.getDataType();
                if (leftRef.getMemberPath() != null) {
                    dtype = DataTypeUtil.getMemberType(dtype, leftRef.getMemberPath());
                }
                if (leftRef.useCalibratedValue()) {
                    dtype.convertType(value);
                } else {
                    dtype.parseStringForRawValue(value);
                }
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid value '" + valueToken.text + "' for "
                        + refToken.text + ": " + e.getMessage(), valueToken.offset);
            }
        } else {
            throw new ParseException("Expected value, found '" + valueToken.text + "'", valueToken.offset);
        }
        return condition;
    }

    private ParameterInstanceRef resolveReference(Token token) throws ParseException {
        var name = token.text;
        var useCalibrated = true;
        if (name.endsWith(".raw")) {
            name = name.substring(0, name.length() - 4);
            useCalibrated = false;
        }

        var parameter = mdb.getParameter(name);
        PathElement[] path = null;
        if (parameter == null) {
            // Maybe an aggregate or array member: /a/b.member[0]
            var slashIdx = name.lastIndexOf('/');
            var memberIdx = -1;
            for (int i = slashIdx + 1; i < name.length(); i++) {
                var c = name.charAt(i);
                if (c == '.' || c == '[') {
                    memberIdx = i;
                    break;
                }
            }
            if (memberIdx > 0) {
                parameter = mdb.getParameter(name.substring(0, memberIdx));
                if (parameter != null) {
                    var pathString = name.substring(memberIdx);
                    try {
                        // Normalize "[0]" to ".[0]" (top-level array index)
                        path = AggregateUtil.parseReference(pathString.startsWith("[")
                                ? pathString
                                : pathString.substring(1));
                    } catch (IllegalArgumentException e) {
                        throw new ParseException("Invalid member path in '" + token.text + "'", token.offset);
                    }
                    if (parameter.getParameterType() == null
                            || !AggregateUtil.verifyPath(parameter.getParameterType(), path)) {
                        throw new ParseException("Unknown member '" + pathString + "' of "
                                + parameter.getQualifiedName(), token.offset);
                    }
                }
            }
        }
        if (parameter == null) {
            throw new ParseException("Unknown parameter '" + name + "'", token.offset);
        }

        parameters.add(parameter);
        var ref = new ParameterInstanceRef(parameter, useCalibrated);
        if (path != null) {
            ref.setMemberPath(path);
        }
        return ref;
    }

    /**
     * XTCE boolean expressions have no negation, so push it down to the comparisons.
     */
    private static BooleanExpression negate(BooleanExpression expr) {
        if (expr instanceof Condition c) {
            var op = negate(c.getComparisonOperator());
            if (c.getRightValue() != null) {
                return new Condition(op, c.getLeftRef(), c.getRightValue());
            } else {
                return new Condition(op, c.getLeftRef(), c.getRightRef());
            }
        } else if (expr instanceof ANDedConditions and) {
            var or = new ORedConditions();
            for (var sub : and.getExpressionList()) {
                or.addConditionExpression(negate(sub));
            }
            return or;
        } else if (expr instanceof ORedConditions or) {
            var and = new ANDedConditions();
            for (var sub : or.getExpressionList()) {
                and.addConditionExpression(negate(sub));
            }
            return and;
        } else {
            throw new IllegalArgumentException("Cannot negate " + expr);
        }
    }

    private static OperatorType negate(OperatorType op) {
        return switch (op) {
        case EQUALITY -> OperatorType.INEQUALITY;
        case INEQUALITY -> OperatorType.EQUALITY;
        case LARGERTHAN -> OperatorType.SMALLEROREQUALTHAN;
        case LARGEROREQUALTHAN -> OperatorType.SMALLERTHAN;
        case SMALLERTHAN -> OperatorType.LARGEROREQUALTHAN;
        case SMALLEROREQUALTHAN -> OperatorType.LARGERTHAN;
        };
    }

    private static OperatorType toOperator(Token token) throws ParseException {
        return switch (token.text.toUpperCase()) {
        case "==", "=", "EQ" -> OperatorType.EQUALITY;
        case "!=", "NE" -> OperatorType.INEQUALITY;
        case "<", "LT" -> OperatorType.SMALLERTHAN;
        case "<=", "LE" -> OperatorType.SMALLEROREQUALTHAN;
        case ">", "GT" -> OperatorType.LARGERTHAN;
        case ">=", "GE" -> OperatorType.LARGEROREQUALTHAN;
        default -> throw new ParseException("Unknown operator '" + token.text + "'", token.offset);
        };
    }

    private boolean peek(TokenType type) {
        return pos < tokens.size() && tokens.get(pos).type == type;
    }

    private boolean accept(TokenType type) {
        if (peek(type)) {
            pos++;
            return true;
        }
        return false;
    }

    private Token next(String expected) throws ParseException {
        if (pos >= tokens.size()) {
            throw new ParseException("Unexpected end of expression, expected " + expected, endOffset());
        }
        return tokens.get(pos++);
    }

    private Token expect(TokenType type, String expected) throws ParseException {
        var token = next(expected);
        if (token.type != type) {
            throw new ParseException("Expected " + expected + ", found '" + token.text + "'", token.offset);
        }
        return token;
    }

    private int endOffset() {
        if (tokens.isEmpty()) {
            return 0;
        }
        var last = tokens.get(tokens.size() - 1);
        return last.offset + last.text.length();
    }

    private static List<Token> tokenize(String s) throws ParseException {
        var result = new ArrayList<Token>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                result.add(new Token(TokenType.LPAREN, "(", i++));
            } else if (c == ')') {
                result.add(new Token(TokenType.RPAREN, ")", i++));
            } else if (s.startsWith("&&", i)) {
                result.add(new Token(TokenType.AND, "&&", i));
                i += 2;
            } else if (s.startsWith("||", i)) {
                result.add(new Token(TokenType.OR, "||", i));
                i += 2;
            } else if (s.startsWith("==", i) || s.startsWith("!=", i)
                    || s.startsWith("<=", i) || s.startsWith(">=", i)) {
                result.add(new Token(TokenType.OPERATOR, s.substring(i, i + 2), i));
                i += 2;
            } else if (c == '<' || c == '>' || c == '=') {
                result.add(new Token(TokenType.OPERATOR, String.valueOf(c), i++));
            } else if (c == '!') {
                result.add(new Token(TokenType.NOT, "!", i++));
            } else if (c == '"' || c == '\'') {
                int start = i;
                var sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < s.length()) {
                    char d = s.charAt(i);
                    if (d == '\\' && i + 1 < s.length()) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                    } else if (d == c) {
                        closed = true;
                        i++;
                        break;
                    } else {
                        sb.append(d);
                        i++;
                    }
                }
                if (!closed) {
                    throw new ParseException("Unterminated string", start);
                }
                result.add(new Token(TokenType.QUOTED, sb.toString(), start));
            } else {
                int start = i;
                while (i < s.length() && !isAtomTerminator(s.charAt(i))) {
                    i++;
                }
                var text = s.substring(start, i);
                result.add(new Token(atomType(text), text, start));
            }
        }
        return result;
    }

    private static boolean isAtomTerminator(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '<' || c == '>' || c == '='
                || c == '!' || c == '&' || c == '|' || c == '"' || c == '\'';
    }

    private static TokenType atomType(String text) {
        return switch (text.toUpperCase()) {
        case "AND" -> TokenType.AND;
        case "OR" -> TokenType.OR;
        case "NOT" -> TokenType.NOT;
        case "EQ", "NE", "LT", "LE", "GT", "GE" -> TokenType.OPERATOR;
        default -> TokenType.ATOM;
        };
    }

    private enum TokenType {
        ATOM, QUOTED, OPERATOR, AND, OR, NOT, LPAREN, RPAREN
    }

    private record Token(TokenType type, String text, int offset) {
    }
}
