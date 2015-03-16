package org.yamcs;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
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
	
		
		
		ParameterValueList pvlist = new ParameterValueList(pvalues);
		
		
		assertEquals(n+1, pvlist.getSize());
		
		ParameterValue pv10 = pvlist.getNewest(params[10]);
		assertEquals(pvalues.get(10), pv10);
		
		ParameterValue pv2 = pvlist.getNewest(params[2]);
		assertEquals(pvalues.get(n), pv2);
	}
}
