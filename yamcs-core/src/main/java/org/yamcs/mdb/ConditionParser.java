package org.yamcs.mdb;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.BooleanExpression;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.ExpressionList;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.util.NameReference;

/**
 * used by the SpreadsheetLoader to parse conditions
 * 
 * 
 * @author nm
 *
 */
public class ConditionParser {
    ParameterReferenceFactory prefFactory;

    public ConditionParser(ParameterReferenceFactory prefFactory) {
        this.prefFactory = prefFactory;
    }

    public MatchCriteria parseMatchCriteria(String criteriaString) throws ParseException {
        criteriaString = criteriaString.trim();
        if ((criteriaString.startsWith("&(") || criteriaString.startsWith("|("))
                && (criteriaString.endsWith(")"))) {
            return parseBooleanExpression(criteriaString);
        } else if (criteriaString.contains(";")) {
            ComparisonList cl = new ComparisonList();
            String splitted[] = criteriaString.split(";");
            for (String part : splitted) {
                cl.addComparison(toComparison(part));
            }
            return cl;
        } else {
            return toComparison(criteriaString);
        }
    }

    /**
     * Boolean expression has the following pattern: op(epx1;exp2;...;expn)
     *
     * op is & (AND) or | (OR) expi are boolean expression or condition
     *
     * A condition is defined as: parametername op value
     *
     * value can be - plain value - quoted with " or ”. The two quote characters can be used interchangeably . Backslash
     * can be use to escape those double quote. - $other_parametername
     *
     * parametername can be suffixed with .raw
     *
     * Top level expression can be in the form epx1;exp2;...;expn which will be transformed into &(epx1;exp2;...;expn)
     * for compatibility with the previously implemented Comparison
     * 
     * @param rawExpression
     * 
     * @return
     * @throws ParseException
     */
    public BooleanExpression parseBooleanExpression(String rawExpression) throws ParseException {
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

        return toBooleanExpression(spec, quotes);
    }

    void parseConditionList(ExpressionList conditions, String spec, ArrayList<String> quotes) throws ParseException {
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
            conditions.addConditionExpression(toBooleanExpression(expression, quotes));
        }
    }

    private BooleanExpression toBooleanExpression(String spec, ArrayList<String> quotes) throws ParseException {
        spec = spec.trim();
        BooleanExpression condition = null;

        if (spec.startsWith("&(") && (spec.endsWith(")"))) {
            condition = new ANDedConditions();
            parseConditionList((ExpressionList) condition, spec.substring(2, spec.length() - 1), quotes);
        } else if (spec.startsWith("|(") && (spec.endsWith(")"))) {
            condition = new ORedConditions();
            parseConditionList((ExpressionList) condition, spec.substring(2, spec.length() - 1), quotes);
        } else {
            condition = toCondition(spec, quotes);
        }

        return condition;
    }

    private Condition toCondition(String comparisonString, ArrayList<String> quotes) throws ParseException {
        Matcher m = Pattern.compile("(.*?)(==|=|!=|<=|>=|<|>)(.*)").matcher(comparisonString);
        if (!m.matches()) {
            throw new ParseException("Cannot parse condition '" + comparisonString + "'", 0);
        }

        String lParamName = m.group(1).trim();
        boolean lParamCalibrated = true;

        if (lParamName.endsWith(".raw")) {
            lParamName = lParamName.substring(0, lParamName.length() - 4);
            lParamCalibrated = false;
        }
        final ParameterInstanceRef lParamRef = new ParameterInstanceRef(lParamCalibrated);

        String op = m.group(2);
        if ("=".equals(op)) {
            op = "==";
        }

        String rValue = m.group(3).trim();
        String rParamName = null;
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

            rParamRef = new ParameterInstanceRef(rParamCalibrated);
            cond = new Condition(OperatorType.fromSymbol(op), lParamRef, rParamRef);
        } else {
            rParamRef = null;
            if ((rValue.startsWith("\"") || rValue.startsWith("”")) &&
                    (rValue.endsWith("\"") || rValue.endsWith("”"))) {
                rValue = rValue.substring(1, rValue.length() - 1);
            } else if ("true".equalsIgnoreCase(rValue)) {
                rValue = BooleanDataType.DEFAULT_ONE_STRING_VALUE;
            } else if ("false".equalsIgnoreCase(rValue)) {
                rValue = BooleanDataType.DEFAULT_ZERO_STRING_VALUE;
            }
            cond = new Condition(OperatorType.fromSymbol(op), lParamRef, rValue);
        }

        if (rParamRef != null) {
            NameReference pref = prefFactory.getReference(rParamName);
            pref.addResolvedAction(nd -> {
                rParamRef.setParameter((Parameter) nd);
            });
        }

        NameReference pref = prefFactory.getReference(lParamName);
        pref.addResolvedAction(nd -> {
            lParamRef.setParameter((Parameter) nd);
            cond.validateValueType();
        });

        return cond;
    }

    public Comparison toComparison(String comparisonString) throws ParseException {
        comparisonString = comparisonString.trim();
        Matcher m = Pattern.compile("(.*?)(==|=|!=|<=|>=|<|>)(.*)").matcher(comparisonString);
        if (!m.matches()) {
            throw new ParseException("Cannot parse condition '" + comparisonString + "'", 0);
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
                throw new ParseException("Cannot parse parameter for comparison '" + pname
                        + "'. Use parameterName or parameterName.raw", 0);
            }
        }

        String op = m.group(2);
        String value = m.group(3).trim();

        if ((value.startsWith("\"") || value.startsWith("”")) &&
                (value.endsWith("\"") || value.endsWith("”"))) {
            value = value.substring(1, value.length() - 1);
        } else if ("true".equalsIgnoreCase(value)) {
            value = BooleanDataType.DEFAULT_ONE_STRING_VALUE;
        } else if ("false".equalsIgnoreCase(value)) {
            value = BooleanDataType.DEFAULT_ZERO_STRING_VALUE;
        }
        if ("=".equals(op)) {
            op = "==";
        }

        OperatorType opType;
        try {
            opType = OperatorType.fromSymbol(op);
        } catch (IllegalArgumentException e) {
            throw new ParseException("Unknown operator '" + op + "'", 0);
        }

        final ParameterInstanceRef pInstRef = new ParameterInstanceRef(useCalibrated);
        final Comparison ucomp = new Comparison(pInstRef, value, opType);

        NameReference pref = prefFactory.getReference(pname);
        pref.addResolvedAction(nd -> {
            pInstRef.setParameter((Parameter) nd);
            ucomp.validateValueType();
        });

        return ucomp;
    }

    public interface ParameterReferenceFactory {
        NameReference getReference(String pname);
    }
}
