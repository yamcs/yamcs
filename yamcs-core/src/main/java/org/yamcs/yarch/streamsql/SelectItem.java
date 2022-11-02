package org.yamcs.yarch.streamsql;

public class SelectItem {
    public static final SelectItem STAR = new SelectItem(null);
    Expression expr;
    String alias;

    public SelectItem(Expression expr) {
        this.expr = expr;
    }

    public void setAlias(String name) {
        this.alias = name;
        expr.setColumnName(name);
    }

    public String getName() {
        if (alias != null)
            return alias;
        else
            return expr.getColumnName();
    }

    public boolean isStar() {
        return this == STAR;
    }

    @Override
    public String toString() {
        if (alias == null)
            return expr.toString();
        else
            return expr.toString() + "(aliased: " + alias + ")";
    }
}
