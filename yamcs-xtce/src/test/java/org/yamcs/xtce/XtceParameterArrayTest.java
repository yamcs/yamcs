package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class XtceParameterArrayTest {

    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        try (XtceStaxReader reader = new XtceStaxReader("src/test/resources/BogusSAT-1.xml")) {
            SpaceSystem ss = reader.readXmlDocument();
            ArrayParameterType pt = (ArrayParameterType) ss.getParameterType("Array_with_numberOfDimensions");
            assertEquals(3, pt.getNumberOfDimensions());
            assertNull(pt.getSize());

            ArrayParameterType pt2 = (ArrayParameterType) ss.getParameterType("Array_with_dimensionList");
            assertEquals(1, pt2.getNumberOfDimensions());
            assertNotNull(pt2.getSize());

            SequenceContainer sq = ss.getSequenceContainer("test_array");
            ArrayParameterEntry ape = (ArrayParameterEntry) sq.getEntryList().get(0);
            assertEquals(pt2.getSize(), ape.getSize());

            ArrayParameterType pt3 = (ArrayParameterType) ss.getParameterType("Array_with_type_defined_later");
            assertNotNull(pt3);
        }
    }
}
