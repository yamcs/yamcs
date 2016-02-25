package org.yamcs.parameter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Parameter;

public class ParameterValueListTest {
    @Test
    public void test1() {
	int n = 10000;
	Parameter[] params = new Parameter[n];
	List<ParameterValue> pvalues= new ArrayList<ParameterValue>(n+1);
	for (int i = 0; i<n; i++) {
	    params[i] = new Parameter("parameter"+i);
	    ParameterValue pv = new ParameterValue(params[i]);
	    pvalues.add(pv);
	}
	ParameterValue pv2bis = new ParameterValue(params[2]);
	pvalues.add(pv2bis);

	
	

	//bulk create
	ParameterValueList pvlist1 = new ParameterValueList(pvalues);
	assertEquals(n+1, pvlist1.getSize());

	ParameterValue pv10 = pvlist1.getLastInserted(params[10]);
	assertEquals(pvalues.get(10), pv10);

	ParameterValue pv2 = pvlist1.getLastInserted(params[2]);
	assertEquals(pvalues.get(n), pv2);
	assertEquals(pvalues.get(2), pvlist1.getFirstInserted(params[2]));
	
	assertEquals(1, pvlist1.count(params[0]));
	assertEquals(2, pvlist1.count(params[2]));
	
	List<ParameterValue> foreachresult1 = new ArrayList<ParameterValue>();
        pvlist1.forEach(params[1], (ParameterValue pv) -> foreachresult1.add(pv));
        assertEquals(Arrays.asList(pvalues.get(1)), foreachresult1);
        
        
	List<ParameterValue> foreachresult2 = new ArrayList<ParameterValue>();
	pvlist1.forEach(params[2], (ParameterValue pv) -> foreachresult2.add(pv));
	assertEquals(Arrays.asList(pvalues.get(2), pvalues.get(n)), foreachresult2);
	
	
	
	List<ParameterValue> pvalues1 = new ArrayList<ParameterValue>(pvalues);
	pvalues1.removeAll(pvlist1);
	assertTrue(pvalues1.isEmpty());

	////////////one by one 
	ParameterValueList pvlist2 = new ParameterValueList();

	for(ParameterValue pv:pvalues) {
	    pvlist2.add(pv);
	}
	assertEquals(n+1, pvlist2.getSize());

	List<ParameterValue> pvalues2 = new ArrayList<ParameterValue>(pvalues);
	pvalues2.removeAll(pvlist2);
	assertTrue(pvalues2.isEmpty());


	//add all
	ParameterValueList pvlist3 = new ParameterValueList();
	pvlist3.addAll(pvalues);
	assertEquals(n+1, pvlist3.getSize());
	List<ParameterValue> pvalues3 = new ArrayList<ParameterValue>(pvalues);
	pvalues3.removeAll(pvlist3);
	assertTrue(pvalues3.isEmpty());	
	
	
	
	
    }

    @Test
    public void testRemove() {
	Parameter p = new Parameter("parameter");
	ParameterValue pv1 = new ParameterValue(p);
	pv1.setStringValue("pv1");
	ParameterValue pv2 = new ParameterValue(p);
	pv2.setStringValue("pv2");
	
	ParameterValueList pvlist = new ParameterValueList();
	pvlist.add(pv1);
	assertEquals(1, pvlist.getSize());
	
	assertEquals(pv1, pvlist.removeFirst(p));
	assertEquals(0, pvlist.getSize());
	pvlist.add(pv1);
	assertEquals(pv1, pvlist.removeLast(p));
	assertEquals(0, pvlist.getSize());
	
	
	pvlist.add(pv1);
	pvlist.add(pv2);
    
	
	assertEquals(pv1, pvlist.removeFirst(p));
	assertEquals(1, pvlist.getSize());
	assertEquals(pv2, pvlist.getLastInserted(p));
	pvlist.add(pv1);
	
	assertEquals(pv1, pvlist.removeLast(p));
	assertEquals(1, pvlist.getSize());
	assertEquals(pv2, pvlist.getLastInserted(p));
	
	
	assertEquals(pv2, pvlist.removeLast(p));
	assertEquals(0, pvlist.getSize());
    }

    
    
    @Test
    public void testRemoveMany() {
	int n = 10;
	int m = 5;
	
	Parameter[] params = new Parameter[2*n];
	List<ParameterValue> pvalues= new ArrayList<ParameterValue>(n*m);
	for (int i = 0; i<2*n; i++) {
	    params[i] = new Parameter("parameter"+i);
	}
	
	for(int j=0;j<m; j++) {
	    for(int i=0;i<2*n; i++) {
		ParameterValue pv = new ParameterValue(params[i]);
		pv.setStringValue(i+":"+j);
		pvalues.add(pv);
	    }
	}

	//bulk create with collisions
	ParameterValueList pvlist = new ParameterValueList(4, pvalues);
	assertEquals(2*n*m, pvlist.getSize());
	for(int j=0; j<m; j++) {
	    for(int i=0;i<n;i++) {
		ParameterValue pv = pvlist.removeFirst(params[i]);
		assertEquals(pvalues.get(j*2*n+i), pv );
	    }
	    for(int i=n;i<2*n;i++) {
		ParameterValue pv = pvlist.removeLast(params[i]);
		assertEquals(pvalues.get((m-j-1)*2*n+i), pv );
	    }
	    assertEquals(2*n*(m-j-1), pvlist.getSize());
	}
    }
    
    @Test
    public void testIterator() {
	Parameter p = new Parameter("p1");
	ParameterValue pv1 = new ParameterValue(p);
	pv1.setStringValue("pv1");
	
	ParameterValue pv2 = new ParameterValue(p);
	pv2.setStringValue("pv2");
	 
	ParameterValueList pvlist = new ParameterValueList();
	
	Iterator<ParameterValue> it = pvlist.iterator();
	assertFalse(it.hasNext());
	
	pvlist.add(pv1);
	it = pvlist.iterator();
	assertTrue(it.hasNext());
	assertEquals(pv1, it.next());
	assertFalse(it.hasNext());
	
	pvlist.removeFirst(p);
	
	pvlist.add(pv2);
	pvlist.add(pv1);
	it = pvlist.iterator();
	
	assertTrue(it.hasNext());
	assertEquals(pv2, it.next());
	assertTrue(it.hasNext());
	assertEquals(pv1, it.next());
	assertFalse(it.hasNext());
    }
    @Test
    public void testIterator1() {
	int n = 10000;
	Parameter[] params = new Parameter[n];
	List<ParameterValue> pvalues= new ArrayList<ParameterValue>(n+1);
	for (int i = 0; i<n; i++) {
	    params[i] = new Parameter("parameter"+i);
	    ParameterValue pv = new ParameterValue(params[i]);
	    pvalues.add(pv);
	}
	
	ParameterValue pv2bis = new ParameterValue(params[2]);
	pvalues.add(pv2bis);
	
	ParameterValueList pvlist = new ParameterValueList(pvalues);
	
	Iterator<ParameterValue> it = pvlist.iterator();
	for (int i = 0; i<n; i++) {
	    assertTrue(it.hasNext());
	    ParameterValue pv = it.next();
	    assertEquals(pvalues.get(i), pv);
	}
	assertTrue(it.hasNext());
	ParameterValue pv = it.next();
	assertEquals(pv2bis, pv);
	assertFalse(it.hasNext());
	

	pvlist.removeLast(params[2]);
	it = pvlist.iterator();
	for (int i = 0; i<n; i++) {
	    assertTrue(it.hasNext());
	    pv = it.next();
	    assertEquals(pvalues.get(i), pv);
	}
	
	assertFalse(it.hasNext());
    }
}
