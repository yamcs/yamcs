package org.yamcs.xtce;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestBaseType {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/basetype.xml");
        
        FloatParameterType ptype = (FloatParameterType) ss.getParameterType("latitude_t");
        FloatDataEncoding encodig = (FloatDataEncoding) ptype.getEncoding();
        assertEquals(64, encodig.getSizeInBits());
        
        FloatArgumentType atype = (FloatArgumentType) ss.getArgumentType("temperature_t");
        encodig = (FloatDataEncoding) atype.getEncoding();
        assertEquals(64, encodig.getSizeInBits());
        
    }
}
