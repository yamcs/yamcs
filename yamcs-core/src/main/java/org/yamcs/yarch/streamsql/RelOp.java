package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.RelOp;

public enum RelOp {
    EQUAL, NOT_EQUAL, GREATER_OR_EQUAL, GREATER, LESS_OR_EQUAL, LESS;
    public RelOp getOppsite() {
        switch(this) {
        case EQUAL:
            return EQUAL;
        case NOT_EQUAL:
            return NOT_EQUAL;
        case GREATER_OR_EQUAL:
            return LESS;
        case GREATER:
            return LESS_OR_EQUAL;
        case LESS_OR_EQUAL:
            return GREATER;
        case LESS:
            return GREATER_OR_EQUAL;
        }
        throw new RuntimeException("there is no such RelOp "+this);
    }
    public String getSign() {
        switch(this) {
        case EQUAL: return "==";
        case GREATER: return ">";
        case GREATER_OR_EQUAL: return ">=";
        case LESS: return "<";
        case LESS_OR_EQUAL: return "<=";
        case NOT_EQUAL: return "!=";
        default: return null;
        }
    }
}
