package org.yamcs.yarch.streamsql;

import java.util.List;

public class InClause {
    boolean negation = false;
    List<Expression> list;
    
    public void setNegation(boolean b) {
        this.negation = b;        
    }
    
    public void setList(List<Expression> list) {
        this.list = list;
        
    }
}
