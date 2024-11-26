package org.yamcs.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.xtce.Parameter;

public class StackedVerify implements Step {

    private List<VerifyComparison> condition = new ArrayList<>();
    private long delay = 0;
    private long timeout = -1;

    public void addComparison(Parameter parameter, String operator, Object value) {
        condition.add(new VerifyComparison(parameter, operator, value));
    }

    public List<VerifyComparison> getCondition() {
        return condition;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return condition.stream()
                .map(VerifyComparison::toString)
                .collect(Collectors.joining(" AND "));
    }

    public static record VerifyComparison(
            Parameter parameter,
            String operator,
            Object value) {

        @Override
        public final String toString() {
            var res = parameter.getQualifiedName();
            switch (operator) {
            case "eq":
                res += " = ";
                break;
            case "neq":
                res += " != ";
                break;
            case "lt":
                res += " < ";
                break;
            case "lte":
                res += " <= ";
                break;
            case "gt":
                res += " > ";
                break;
            case "gte":
                res += " >= ";
                break;
            default:
                res += operator;
            }
            res += value;
            return res;
        }
    }
}
