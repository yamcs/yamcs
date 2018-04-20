package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.yamcs.xtceproc.XtceDbFactory;

public class XmlLoaderTest {

    @Test
    public void test1() throws Exception {        
        XtceDb db = XtceDbFactory.createInstanceByConfig("ccsds-green-book");
        Parameter pmt = db.getParameter("/SpaceVehicle/MissionTime");
        assertTrue(pmt.getParameterType() instanceof AbsoluteTimeParameterType);
        
        Parameter cst = db.getParameter("/SpaceVehicle/CheckSum");
        assertTrue(cst.getParameterType() instanceof IntegerParameterType);
        IntegerParameterType ipt = (IntegerParameterType) cst.getParameterType();
        assertEquals(8, ipt.getEncoding().getSizeInBits());
        assertEquals(DataSource.DERIVED, cst.getDataSource());
        
        
        Parameter pms = db.getParameter("/SpaceVehicle/Seconds");
        assertTrue(pms.getParameterType() instanceof AbsoluteTimeParameterType);
        AbsoluteTimeParameterType ptype = (AbsoluteTimeParameterType) pms.getParameterType();
        ReferenceTime rtime = ptype.getReferenceTime();
        assertEquals(TimeEpoch.CommonEpochs.TAI, rtime.getEpoch().getCommonEpoch());
        
        DataEncoding encoding = ptype.getEncoding();
        assertTrue(encoding instanceof IntegerDataEncoding);
        assertEquals(32, ((IntegerDataEncoding) encoding).getSizeInBits());
        
        
        MetaCommand cmd1 = db.getMetaCommand("/SpaceVehicle/PWHTMR");
        CommandContainer cc = cmd1.getCommandContainer();
        List<SequenceEntry> sel = cc.getEntryList();
        assertEquals(3, sel.size());
        
        assertEquals("Header", cc.getBaseContainer().getName());
        assertTrue(cc.getRestrictionCriteria() instanceof ComparisonList);
    }



}
