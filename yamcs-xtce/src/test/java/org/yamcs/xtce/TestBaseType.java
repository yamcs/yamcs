package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteOrder;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestBaseType {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        try (XtceStaxReader reader = new XtceStaxReader("src/test/resources/basetype.xml")) {
            SpaceSystem ss = reader.readXmlDocument();

            FloatParameterType ptype = (FloatParameterType) ss.getParameterType("latitude_t");
            FloatDataEncoding encoding = (FloatDataEncoding) ptype.getEncoding();
            assertEquals(64, encoding.getSizeInBits());

            FloatArgumentType atype = (FloatArgumentType) ss.getArgumentType("temperature_t");
            encoding = (FloatDataEncoding) atype.getEncoding();
            assertEquals(64, encoding.getSizeInBits());

            ptype = (FloatParameterType) ss.getParameterType("latitude_rad_t");
            encoding = (FloatDataEncoding) ptype.getEncoding();
            assertEquals(64, encoding.getSizeInBits());
            assertEquals(ByteOrder.BIG_ENDIAN, encoding.byteOrder);

            PolynomialCalibrator calib = (PolynomialCalibrator) encoding.getDefaultCalibrator();
            assertArrayEquals(new double[] { 0, 0.01745329251994329576 }, calib.getCoefficients(), 1e-6);

            ptype = (FloatParameterType) ss.getParameterType("test_lsb");
            encoding = (FloatDataEncoding) ptype.getEncoding();
            assertEquals(64, encoding.getSizeInBits());
            assertEquals(ByteOrder.LITTLE_ENDIAN, encoding.byteOrder);

            calib = (PolynomialCalibrator) encoding.getDefaultCalibrator();
            assertArrayEquals(new double[] { 0, 0.01745329251994329576 }, calib.getCoefficients(), 1e-6);
        }
    }
}
