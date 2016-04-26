package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

public class SqlBuilder {
    
    private String table;
    private List<String> selectExpressions;
    private List<String> conditions;
    private Boolean descend;
    
    public SqlBuilder(String table) {
        this.table = table;
    }
    
    /**
     * Additive! Calling multiple times will add extra select expressions to the alread specified list.
     */
    public SqlBuilder select(String... exprs) {
        if (selectExpressions == null) {
            selectExpressions = new ArrayList<>(exprs.length);
        }
        for (String expr : exprs) {
            selectExpressions.add(expr);
        }
        return this;
    }
    
    /**
     * Additive! Calling multiple times will add extra conditions to the already specified list.
     */
    public SqlBuilder where(String... whereCondition) {
        if (conditions == null) {
            conditions = new ArrayList<>(whereCondition.length);
        }
        for (String cond : whereCondition) {
            conditions.add(cond);
        }
        return this;
    }
    
    public SqlBuilder descend(boolean descend) {
        this.descend = descend;
        return this;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("select ");
        if (selectExpressions == null) {
            buf.append("*");
        } else {
            boolean first = true;
            for (String expr : selectExpressions) {
                if (!first) buf.append(", ");
                buf.append(expr);
                first = false;
            }
        }
        buf.append(" from ").append(table);
        if (conditions != null) {
            buf.append(" where ");
            boolean first = true;
            for (String condition : conditions) {
                if (!first) buf.append(" and ");
                else first=false;
                buf.append(condition);
            }
        }
        if (descend != null) {
            buf.append(descend ? " order desc" : " order asc");
        }
        return buf.toString();
    }
}
