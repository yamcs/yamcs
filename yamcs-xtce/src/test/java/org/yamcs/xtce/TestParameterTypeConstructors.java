package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * Test copy of parameter types
 * 
 * @author nm
 *
 */
public class TestParameterTypeConstructors {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader("src/test/resources/BogusSAT-1.xml");
        SpaceSystem ss = reader.readXmlDocument();
        for (Parameter p : ss.getParameters()) {
            ParameterType t1 = p.getParameterType();
            if (t1 == null) {
                continue;
            }
            ParameterType t2 = clone(t1);
            assertNotEquals(t1, t2);
            try {
                verifyEquals(t1, t2);
            } catch (AssertionError e) {
                throw new AssertionError("failed to verify " + t1 + ": " + e.getMessage());
            }
        }
    }

    ParameterType clone(ParameterType ptype) {
        if (ptype instanceof BinaryParameterType) {
            BinaryParameterType t = (BinaryParameterType) ptype;
            return new BinaryParameterType(t);

        } else if (ptype instanceof BooleanParameterType) {
            BooleanParameterType t = (BooleanParameterType) ptype;
            return new BooleanParameterType(t);

        } else if (ptype instanceof EnumeratedParameterType) {
            EnumeratedParameterType t = (EnumeratedParameterType) ptype;
            return new EnumeratedParameterType(t);

        } else if (ptype instanceof FloatParameterType) {
            FloatParameterType t = (FloatParameterType) ptype;
            return new FloatParameterType(t);

        } else if (ptype instanceof IntegerParameterType) {
            IntegerParameterType t = (IntegerParameterType) ptype;
            return new IntegerParameterType(t);

        } else if (ptype instanceof StringParameterType) {
            StringParameterType t = (StringParameterType) ptype;
            return new StringParameterType(t);
        } else if (ptype instanceof ArrayParameterType) {
            ArrayParameterType t = (ArrayParameterType) ptype;
            return new ArrayParameterType(t);
        } else {
            throw new IllegalArgumentException("Cannot clone type " + ptype);
        }
    }

    @SuppressWarnings("rawtypes")
    private void verifyEquals(ParameterType p1, ParameterType p2)
            throws IllegalArgumentException, IllegalAccessException {
        Class c1 = p1.getClass();
        Class c2 = p2.getClass();
        while (true) {
            assertEquals(c1, c2);
            Field[] fa = c1.getDeclaredFields();
            for (Field f : fa) {
                f.setAccessible(true);
                assertEquals(f.get(p1), f.get(p2), "When comparing " + f.getName());
            }
            c1 = c1.getSuperclass();
            c2 = c2.getSuperclass();
            if (c1 == null) {
                break;
            }
        }
    }
}
