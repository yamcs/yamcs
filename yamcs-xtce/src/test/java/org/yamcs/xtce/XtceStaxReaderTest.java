package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.xml.XtceStaxReader;

public class XtceStaxReaderTest {
    @Test
    public void testBogusSat() throws XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/BogusSAT-1.xml");
        assertNotNull(ss);
        
        SpaceSystem sc001 = ss.getSubsystem("SC001"); 
        assertNotNull(sc001);
        
        SpaceSystem busElectronics = sc001.getSubsystem("BusElectronics");
        assertNotNull(busElectronics);
        SpaceSystem payload1 = sc001.getSubsystem("Payload1");
        assertNotNull(payload1);
        SpaceSystem payload2 = sc001.getSubsystem("Payload2");
        assertNotNull(payload2);
        
        
        Parameter p = busElectronics.getParameter("Bus_Fault_Message");
        assertNotNull(p);
        assertEquals(p.getParameterType().getClass(), StringParameterType.class);
        StringParameterType sp = (StringParameterType)p.getParameterType();
        assertEquals(sp.encoding.getClass(), StringDataEncoding.class);
        StringDataEncoding sde = (StringDataEncoding) sp.encoding;
        assertEquals(SizeType.Fixed, sde.getSizeType());
        assertEquals(128, sde.getSizeInBits());
        
        p = payload1.getParameter("Payload_Fault_Message");
        assertNotNull(p);
        assertEquals(p.getParameterType().getClass(), StringParameterType.class);
        sp = (StringParameterType)p.getParameterType();
        assertEquals(sp.encoding.getClass(), StringDataEncoding.class);
        sde = (StringDataEncoding) sp.encoding;
        assertEquals(SizeType.TerminationChar, sde.getSizeType());
        assertEquals(0, sde.getTerminationChar());
        
        System.out.println("ss: "+ss);
    }
}
