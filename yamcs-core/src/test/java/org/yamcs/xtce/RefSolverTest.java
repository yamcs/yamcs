package org.yamcs.xtce;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.xtceproc.XtceDbFactory;

public class RefSolverTest {

    @Test
    public void test1() {
        XtceDb db = XtceDbFactory.createInstanceByConfig("xtce-refsolver");
        Parameter para = db.getParameter("/refsolver1/bool1");
        BooleanParameterType ptype = (BooleanParameterType) para.getParameterType();
        IntegerDataEncoding encoding = (IntegerDataEncoding) ptype.getEncoding();
        assertEquals(12, encoding.getSizeInBits());
    }
}
