package org.yamcs.cmdhistory;

/**
 replace almost unchanged the corba stuff to get rid of dependencies
*/
public final class Filter {
    public enum Relation {AVAILABLE, NOT_AVAILABLE, EQUALS, GREATER_THAN, LESS_THAN, LIKE, BETWEEN, ONE_OF};
    public String key = null;
    public String[] values=null;
    public Relation rel;

    public Filter (String key, Relation rel, String value) {
        this.key=key;
        this.rel=rel;
        this.values=new String[]{value};
    }    
    public Filter (String key, Relation rel, String[] values) {
        this.key=key;
        this.rel=rel;
        this.values=values;
    }
    public Filter(String key, Relation rel) {
        this.key=key;
        this.rel=rel;
        this.values=new String[0];
    }
} 
