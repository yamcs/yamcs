package org.yamcs.yarch;

public class IndexFilter {
    //currently only supported is filtering on the first column part of the primary index 
	Comparable keyStart=null;
	Comparable keyEnd=null;
	boolean strictStart, strictEnd;
	
	@Override
	public String toString() {
	    return "keyStart: "+keyStart+" strictStart:"+strictStart+ " keyEnd: "+keyEnd+" strictEnd: "+strictEnd;
	}
}
