package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.BooleanExpression;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.ExpressionList;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.UnresolvedNameReference;

/**
 * used by the SpreadsheetLoader to parse conditions
 * 
 * 
 * @author nm
 *
 */
public class ConditionParser {
    final SpreadsheetLoadContext ctx;

    public ConditionParser(SpreadsheetLoadContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Boolean expression has the following pattern: op(epx1;exp2;...;expn)
     *
     * op is & (AND) or | (OR)
     * expi are boolean expression or condition
     *
     * A condition is defined as: parametername op value
     *
     * value can be
     * - plain value
     * - quoted with " or ”. The two quote characters can be used interchangeably . Backslash can be use to escape those
     * double quote.
     * - $other_parametername
     *
     * parametername can be suffixed with .raw
     *
     * Top level expression can be in the form epx1;exp2;...;expn which will be transformed into &(epx1;exp2;...;expn)
     * for
     * compatibility with the previously implemented Comparison
     * 
     * @param rawExpression
     * 
     * @return
     */
    public BooleanExpression parseBooleanExpression(SpaceSystem spaceSystem, String rawExpression) {
        String regex = "([\"”])([^\"”\\\\]*(?:\\\\.[^\"”\\\\]*)*)([\"”])";

        rawExpression = rawExpression.trim();

        // Correct top-level expression
        if (!rawExpression.startsWith("&") && !rawExpression.startsWith("|")) {
            rawExpression = "&(" + rawExpression + ")";
        }

        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(rawExpression);
        ArrayList<String> quotes = new ArrayList<>();
        while (m.find()) {
            quotes.add(rawExpression.substring(m.start(2), m.end(2)));
        }

        String spec = p.matcher(rawExpression).replaceAll("\\$\\$");

        return toBooleanExpression(spaceSystem, spec, quotes);
    }

    void parseConditionList(SpaceSystem spaceSystem, ExpressionList conditions, String spec, ArrayList<String> quotes) {
        // Split top-level expressions
        ArrayList<String> expressions = new ArrayList<>();
        int balance = 0;
        StringBuilder exp = new StringBuilder();
        for (int i = 0; i < spec.length(); i++) {
            if (spec.charAt(i) == '(') {
                balance++;
            } else if (spec.charAt(i) == ')') {
                balance--;
            } else if ((spec.charAt(i) == ';') && (balance == 0)) {
                if (exp.length() > 0) {
                    expressions.add(exp.toString());
                }
                exp = new StringBuilder();
                continue;
            }

            exp.append(spec.charAt(i));
        }

        if (exp.length() > 0) {
            expressions.add(exp.toString());
        }

        // Parse each expression
        for (String expression : expressions) {
            conditions.addConditionExpression(toBooleanExpression(spaceSystem, expression, quotes));
        }
    }

    private BooleanExpression toBooleanExpression(SpaceSystem spaceSystem, String spec, ArrayList<String> quotes) {
        spec = spec.trim();
        BooleanExpression condition = null;

        if (spec.startsWith("&(") && (spec.endsWith(")"))) {
            condition = new ANDedConditions();
            parseConditionList(spaceSystem, (ExpressionList) condition, spec.substring(2, spec.length() - 1), quotes);
        } else if (spec.startsWith("|(") && (spec.endsWith(")"))) {
            condition = new ORedConditions();
            parseConditionList(spaceSystem, (ExpressionList) condition, spec.substring(2, spec.length() - 1), quotes);
        } else {
            condition = toCondition(spaceSystem, spec, quotes);
        }

        return condition;
    }

    private Condition toCondition(SpaceSystem spaceSystem, String comparisonString, ArrayList<String> quotes) {
        Matcher m = Pattern.compile("(.*?)(==|=|!=|<=|>=|<|>)(.*)").matcher(comparisonString);
        if (!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot parse condition '" + comparisonString + "'");
        }

        String lParamName = m.group(1).trim();
        boolean lParamCalibrated = true;

        if (lParamName.endsWith(".raw")) {
            lParamName = lParamName.substring(0, lParamName.length() - 4);
            lParamCalibrated = false;
        }
        Parameter lParam = spaceSystem.getParameter(lParamName);
        final ParameterInstanceRef lParamRef = new ParameterInstanceRef(lParam, lParamCalibrated);

        String op = m.group(2);
        if ("=".equals(op)) {
            op = "==";
        }

        String rValue = m.group(3).trim();
        String rParamName = null;
        Parameter rParam = null;
        final ParameterInstanceRef rParamRef;
        final Condition cond;

        if (rValue.startsWith("$$")) { // Quoted values
            rValue = quotes.remove(0);
        }

        if (rValue.startsWith("$")) {
            boolean rParamCalibrated = true;
            rParamName = rValue.substring(1);
            if (rParamName.endsWith(".raw")) {
                rParamName = rParamName.substring(0, rParamName.length() - 4);
                rParamCalibrated = false;
            }

            rParam = spaceSystem.getParameter(rParamName);
            rParamRef = new ParameterInstanceRef(rParam, rParamCalibrated);
            cond = new Condition(OperatorType.stringToOperator(op), lParamRef, rParamRef);
        } else {
            rParamRef = null;
            if ((rValue.startsWith("\"") || rValue.startsWith("”")) &&
                    (rValue.endsWith("\"") || rValue.endsWith("”"))) {
                rValue = rValue.substring(1, rValue.length() - 1);
            }
            cond = new Condition(OperatorType.stringToOperator(op), lParamRef, rValue);
        }

        if ((rParamRef != null) && (rParam == null)) {
            spaceSystem.addUnresolvedReference(
                    new UnresolvedNameReference(rParamName, Type.PARAMETER).addResolvedAction(nd -> {
                        rParamRef.setParameter((Parameter) nd);
                        return true;
                    }));
        }

        if (lParam == null) {
            spaceSystem.addUnresolvedReference(
                    new UnresolvedNameReference(lParamName, Type.PARAMETER).addResolvedAction(nd -> {
                        lParamRef.setParameter((Parameter) nd);
                        cond.resolveValueType();
                        return true;
                    }));
        } else {
            cond.resolveValueType();
        }

        return cond;
    }

    public Comparison toComparison(SpaceSystem spaceSystem, String comparisonString) {
        Matcher m = Pattern.compile("(.*?)(==|=|!=|<=|>=|<|>)(.*)").matcher(comparisonString);
        if (!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot parse condition '" + comparisonString + "'");
        }
        String pname = m.group(1).trim();
        boolean useCalibrated = true;
        int idx = pname.indexOf('.');
        if (idx != -1) {
            String t = pname.substring(idx + 1);
            if ("raw".equals(t)) {
                pname = pname.substring(0, idx);
                useCalibrated = false;
            } else {
                throw new SpreadsheetLoadException(ctx, "Cannot parse parameter for comparison '" + pname
                        + "'. Use parameterName or parameterName.raw");
            }
        }

        String op = m.group(2);
        String value = m.group(3).trim();

        if ((value.startsWith("\"") || value.startsWith("”")) &&
                (value.endsWith("\"") || value.endsWith("”"))) {
            value = value.substring(1, value.length() - 1);
        }
        if ("=".equals(op)) {
            op = "==";
        }
        OperatorType opType = Comparison.stringToOperator(op);
        if (opType == null) {
            throw new SpreadsheetLoadException(ctx, "Unknown operator '" + op + "'");
        }

        final ParameterInstanceRef pInstRef = new ParameterInstanceRef(useCalibrated);
        final Comparison ucomp = new Comparison(pInstRef, value, opType);

        NameReference pref = BaseSpreadsheetLoader.getParameterReference(spaceSystem, pname, true);
        pref.addResolvedAction(nd -> {
            pInstRef.setParameter((Parameter) nd);
            ucomp.resolveValueType();
            return true;
        });

        return ucomp;
    }

}
