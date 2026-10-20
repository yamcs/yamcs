package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class DtdNotSupportedTest {

    @Test
    public void testDoctypeIsRejected() {
        // XTCE does not use DTDs; a DOCTYPE declaration (e.g. carrying an
        // entity-expansion payload) must be rejected instead of expanded
        assertThrows(XMLStreamException.class, () -> {
            try (XtceStaxReader reader = new XtceStaxReader("src/test/resources/dtd.xml")) {
                reader.readXmlDocument();
            }
        });
    }
}
