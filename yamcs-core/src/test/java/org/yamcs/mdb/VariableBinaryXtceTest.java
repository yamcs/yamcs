package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.xml.XtceLoadException;

/**
 * Tests that an XTCE document with a variable-length binary data types can be parsed successfully.
 */
public class VariableBinaryXtceTest {

    private static final String SIZE_QN = "/VariableBinaryTest/size";
    private static final String DATA_QN = "/VariableBinaryTest/data";

    private static final String COMMAND_QN = "/VariableBinaryTest/Command";

    private Mdb mdb;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("VariableBinaryTest");

        TimeEncoding.setUp();
    }

    @Test
    public void testReadXtce() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        Parameter dataParameter = mdb.getParameter(DATA_QN);
        ParameterType parameterType = dataParameter.getParameterType();
        DataEncoding de = parameterType.getEncoding();
        assertTrue(de instanceof BinaryDataEncoding);
        BinaryDataEncoding bde = (BinaryDataEncoding) de;
        assertTrue(bde.isVariableSize());

        Parameter sizeParameter = mdb.getParameter(SIZE_QN);
        assertEquals(sizeParameter.getQualifiedName(), bde.getDynamicSize().getDynamicInstanceRef().getName());

        Argument dataArgument = mdb.getMetaCommand(COMMAND_QN)
                .getArgument("data");
        ArgumentType argumentType = dataArgument.getArgumentType();
        assertTrue(argumentType instanceof BinaryArgumentType);
        BinaryArgumentType binaryType = (BinaryArgumentType) argumentType;
        de = binaryType.getEncoding();
        assertTrue(de instanceof BinaryDataEncoding);
        bde = (BinaryDataEncoding) de;
        assertTrue(bde.isVariableSize());

        Argument sizeArgument = mdb.getMetaCommand(COMMAND_QN)
                .getArgument("size");
        assertEquals(sizeArgument.getName(), bde.getDynamicSize().getDynamicInstanceRef().getName());
    }
}
