package org.yamcs.ui.packetviewer.filter;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.ui.packetviewer.ListPacket;
import org.yamcs.ui.packetviewer.filter.ast.AndExpression;
import org.yamcs.ui.packetviewer.filter.ast.Comparison;
import org.yamcs.ui.packetviewer.filter.ast.OrExpression;
import org.yamcs.ui.packetviewer.filter.ast.UnaryExpression;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

public class PacketFilter {

    private final FilterParser parser;
    private final OrExpression expression;
    private Mdb mdb;

    public PacketFilter(String expression, Mdb mdb) throws ParseException {
        parser = new FilterParser(new StringReader(expression));
        this.expression = parser.compileExpression();
        this.mdb = mdb;
    }

    @SuppressWarnings("unchecked")
    public Set<Parameter> getParameters() {
        Set<Parameter> parameters = new HashSet<>();
        if (mdb != null) {
            for (String ref : (Set<String>) parser.references) {
                if (ref.startsWith("/")) {
                    Parameter parameter = mdb.getParameter(ref);
                    if (parameter != null) {
                        parameters.add(parameter);
                    }
                } else if (!ref.startsWith("packet.")) {
                    // User provided a 'short' name. We can't uniquely determine
                    // a parameter, so just subscribe to all of them that match.
                    // Once we have a packet, we'll re-match to a parameter that
                    // exists in the actual packet.
                    for (Parameter parameter : mdb.getParameters()) {
                        if (parameter.getName().equals(ref)) {
                            parameters.add(parameter);
                        }
                    }
                }
            }
        }
        return parameters;
    }

    public boolean matches(ListPacket packet) {
        return matchOrExpression(expression, packet);
    }

    private boolean matchOrExpression(OrExpression expression, ListPacket packet) {
        for (AndExpression clause : expression.getClauses()) {
            if (matchAndExpression(clause, packet)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchAndExpression(AndExpression expression, ListPacket packet) {
        for (UnaryExpression clause : expression.getClauses()) {
            if (!matchUnaryExpression(clause, packet)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchUnaryExpression(UnaryExpression expression, ListPacket packet) {
        boolean res;
        if (expression.getComparison() != null) {
            res = matchComparison(expression.getComparison(), packet);
        } else {
            res = matchOrExpression(expression.getOrExpression(), packet);
        }
        return expression.isNot() ? !res : res;
    }

    private boolean matchComparison(Comparison expression, ListPacket packet) {
        String value = null;
        switch (expression.ref) {
        case "packet.name":
            value = packet.getName();
            break;
        case "packet.length":
            value = Integer.toString(packet.getLength());
            break;
        default:
            Parameter parameter;
            if (expression.ref.startsWith("/")) {
                parameter = mdb.getParameter(expression.ref);
            } else {
                // Search for a short param name directly inside the packet
                // (slow and inaccurate. But convenient)
                parameter = packet.getParameterForShortName(expression.ref);
            }

            // Filter out also packets that do not include a filtered parameter
            if (parameter == null) {
                return false;
            }

            ParameterValue pval = packet.getParameterColumn(parameter);
            if (pval.getEngValue() != null) {
                value = pval.getEngValue().toString();
            }
        }

        if (expression.op == null) {
            // Parameter exists in packet (with any value)
            return true;
        } else {
            switch (expression.op) {
            case EQUAL_TO:
                return value.equals(expression.comparand);
            case NOT_EQUAL_TO:
                return !value.equals(expression.comparand);
            case GREATER_THAN:
                return Double.valueOf(value).compareTo(Double.valueOf(expression.comparand)) > 0;
            case GREATER_THAN_OR_EQUAL_TO:
                return Double.valueOf(value).compareTo(Double.valueOf(expression.comparand)) >= 0;
            case LESS_THAN:
                return Double.valueOf(value).compareTo(Double.valueOf(expression.comparand)) < 0;
            case LESS_THAN_OR_EQUAL_TO:
                return Double.valueOf(value).compareTo(Double.valueOf(expression.comparand)) <= 0;
            case CONTAINS:
                return value.toLowerCase().contains(expression.comparand.toLowerCase());
            case MATCHES:
                Pattern pattern = Pattern.compile(expression.comparand, Pattern.CASE_INSENSITIVE);
                return pattern.matcher(value).matches();
            default:
                throw new IllegalStateException("Unexpected operator " + expression.op);
            }
        }
    }

    @Override
    public String toString() {
        return expression.toString("");
    }
}
