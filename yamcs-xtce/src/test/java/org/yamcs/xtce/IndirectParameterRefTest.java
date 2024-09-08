package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class IndirectParameterRefTest {
    @Test
    public void testIndirectParameterRef() throws Exception {
        try (var reader = new XtceStaxReader("src/test/resources/indirect-param.xml")) {
            var ss = reader.readXmlDocument();
            var idValuePair = ss.getSequenceContainer("id_value_pair");
            assertNotNull(idValuePair);
            assertEquals(2, idValuePair.entryList.size());
 
            var idEntry = (ParameterEntry) idValuePair.entryList.get(0);
            assertEquals("id", idEntry.getParameter().name);

            var indirectEntry = (IndirectParameterRefEntry) idValuePair.entryList.get(1);
            assertNotNull(indirectEntry.getReferenceLocation());
            var ref = indirectEntry.getParameterRef();
            assertNotNull(ref);
            assertEquals("id", ref.getParameter().name);
        }
    }

    @Test
    public void testIndirectParameterRefBadXml() throws Exception {
        try (var reader = new XtceStaxReader("src/test/resources/ipre-no-instance.xml")) {
            assertThrows(XMLStreamException.class, () -> {
                reader.readXmlDocument();
            });
        }
    }
}
