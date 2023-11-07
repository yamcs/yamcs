package org.yamcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class YConfigurationTest {
    @Test
    public void testBinaryDefault() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x01, 0x0A }, config.getBinary("binary-nonexistent", new byte[] { 0x01, 0x0A }));
    }

    @Test
    public void testEmptyBinary() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] {}, config.getBinary("emptyBinary"));
    }

    @Test
    public void testBinary1() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x01, (byte) 0xAB }, config.getBinary("binary1"));
    }

    @Test
    public void testBinary2() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x02, (byte) 0xCD, 0x03 }, config.getBinary("binary2"));
    }

    @Test
    public void testInvalidBinary() {
        assertThrows(ConfigurationException.class, () -> {
            YConfiguration config = YConfiguration.getConfiguration("test-config");
            assertArrayEquals(new byte[] { 0x01, (byte) 0xAB }, config.getBinary("invalid-binary1"));
        });
    }

    @Test
    public void testStringPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property1: ${foo}
        System.setProperty("foo", "stringValue");
        assertEquals("stringValue", config.getString("property1"));

        System.clearProperty("foo");
        assertThrows(ConfigurationException.class, () -> {
            config.getString("property1");
        });

        // property2: "${foo}"
        System.setProperty("foo", "stringValue");
        assertEquals("stringValue", config.getString("property2"));

        // property3: ${foo:defaultValue}
        assertEquals("stringValue", config.getString("property3"));
        System.clearProperty("foo");
        assertEquals("defaultValue", config.getString("property3"));

        // property7: "${foo}/${bar}"
        System.setProperty("foo", "abc");
        System.setProperty("bar", "def");
        assertEquals("abc/def", config.getString("property7"));

        // property9: ${foo: a value with spaces }
        System.clearProperty("foo");
        assertEquals(" a value with spaces ", config.getString("property9"));

        // property10: ${foo:}
        System.clearProperty("foo");
        assertEquals("", config.getString("property10"));
    }

    @Test
    public void testNestedPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property11: ${foo:${bar}}
        System.setProperty("bar", "stringValue");
        assertEquals("stringValue", config.getString("property11"));
        System.setProperty("foo", "abc");
        assertEquals("abc", config.getString("property11"));

        // property12: ${foo:${bar:defaultValue}}
        System.clearProperty("foo");
        System.clearProperty("bar");
        assertEquals("defaultValue", config.getString("property12"));

        // property13: ${foo:${bar:${baz}}}
        System.setProperty("bar", "abc");
        System.setProperty("baz", "def");
        assertEquals("abc", config.getString("property13"));
        System.clearProperty("bar");
        assertEquals("def", config.getString("property13"));
    }

    @Test
    public void testBooleanPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property1: ${foo}
        System.setProperty("foo", "true");
        assertTrue(config.getBoolean("property1"));
        System.setProperty("foo", "false");
        assertFalse(config.getBoolean("property1"));

        System.clearProperty("foo");
        assertThrows(ConfigurationException.class, () -> {
            config.getBoolean("property1");
        });

        // property4: ${foo:true}
        // property5: ${foo:false}
        assertTrue(config.getBoolean("property4"));
        assertFalse(config.getBoolean("property5"));
    }

    @Test
    public void testIntPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property1: ${foo}
        System.setProperty("foo", "10");
        assertEquals(10, config.getInt("property1"));

        System.clearProperty("foo");
        assertThrows(ConfigurationException.class, () -> {
            config.getInt("property1");
        });

        // property6: ${foo:20}
        System.setProperty("foo", "10");
        assertEquals(10, config.getInt("property6"));
        System.clearProperty("foo");
        assertEquals(20, config.getInt("property6"));
        assertEquals("20", config.getString("property6"));
    }

    @Test
    public void testLongPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property1: ${foo}
        System.setProperty("foo", "10");
        assertEquals(10L, config.getLong("property1"));

        System.clearProperty("foo");
        assertThrows(ConfigurationException.class, () -> {
            config.getLong("property1");
        });

        // property6: ${foo:20}
        System.setProperty("foo", "10");
        assertEquals(10L, config.getLong("property6"));
        System.clearProperty("foo");
        assertEquals(20L, config.getLong("property6"));
        assertEquals("20", config.getString("property6"));
    }

    @Test
    public void testDoublePropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property1: ${foo}
        System.setProperty("foo", "10");
        assertEquals(10.0, config.getDouble("property1"));

        System.clearProperty("foo");
        assertThrows(ConfigurationException.class, () -> {
            config.getDouble("property1");
        });

        // property6: ${foo:20}
        System.setProperty("foo", "10");
        assertEquals(10.0, config.getDouble("property6"));
        System.clearProperty("foo");
        assertEquals(20.0, config.getDouble("property6"));
        assertEquals("20", config.getString("property6"));
    }

    @Test
    public void testListPropertyExpansion() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");

        // property8: ["${foo}", ${bar}]
        System.setProperty("foo", "abc");
        System.setProperty("bar", "def");
        List<String> list = config.getList("property8");
        assertEquals("abc", list.get(0));
        assertEquals("def", list.get(1));
    }
}
