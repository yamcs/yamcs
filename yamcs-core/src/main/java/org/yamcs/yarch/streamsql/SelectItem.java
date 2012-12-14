package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.SelectItem;


public class SelectItem {
	public static final SelectItem STAR = new SelectItem(null);
	Expression expr;
	String alias;

	public SelectItem(Expression expr) {
		this.expr=expr;
	}

	public void setAlias(String name) {
		this.alias=name;
	}

	public String getName() {
		if(alias!=null) return alias;
		else return expr.toString();
	}
	
	
	@Override
    public String toString() {
	    if(alias==null)return expr.toString();
	    else return expr.toString() +"(aliased: )"+alias+")";
	}
}
