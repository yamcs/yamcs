package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.RelOp;

public enum RelOp {
    EQUAL, NOT_EQUAL, GREATER_OR_EQUAL, GREATER, LESS_OR_EQUAL, LESS, OVERLAP;

    public RelOp getOppsite() {
        switch (this) {
        case EQUAL:
            return EQUAL;
        case NOT_EQUAL:
            return NOT_EQUAL;
        case GREATER_OR_EQUAL:
            return LESS_OR_EQUAL;
        case GREATER:
            return LESS;
        case LESS_OR_EQUAL:
            return GREATER_OR_EQUAL;
        case LESS:
            return GREATER_OR_EQUAL;
        default:
            throw new IllegalStateException("No opposite for " + this);
        }
    }

    public String getSign() {
        switch (this) {
        case EQUAL:
            return "==";
        case GREATER:
            return ">";
        case GREATER_OR_EQUAL:
            return ">=";
        case LESS:
            return "<";
        case LESS_OR_EQUAL:
            return "<=";
        case NOT_EQUAL:
            return "!=";
        case OVERLAP:
            return "&&";
        default:
            return null;
        }
    }
}
