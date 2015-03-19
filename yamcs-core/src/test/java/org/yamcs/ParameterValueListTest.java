package org.yamcs;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
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
			ParameterValue pv = new ParameterValue(params[i], false);
			pvalues.add(pv);
		}
		ParameterValue pv2bis = new ParameterValue(params[2], false);
		pvalues.add(pv2bis);
	
		
		//bulk create
		ParameterValueList pvlist1 = new ParameterValueList(pvalues);
		assertEquals(n+1, pvlist1.getSize());
		
		ParameterValue pv10 = pvlist1.getNewest(params[10]);
		assertEquals(pvalues.get(10), pv10);
		
		ParameterValue pv2 = pvlist1.getNewest(params[2]);
		assertEquals(pvalues.get(n), pv2);
	
		
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
}
