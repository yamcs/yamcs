package org.yamcs.utils;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class TemplateProcessorTest {

    @Test
    public void testProcess() {
        String template = "Hello {{ a }} and {{b}}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsetVariable() {
        String template = "Hello {{ a }} and {{b}}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        TemplateProcessor.process(template, args);
    }

    @Test
    public void testIfCondition() {
        String template = "Hello {{ a }}{% if b %} and {{ b }}{% endif %}";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testIfElseCondition() {
        String template = "Hello {% if a %}{{ a }}{% else %}{{ b }}{% endif %}";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("b", "YY");
        assertEquals("Hello YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testIfElifElseCondition() {
        String template = "Hello {% if a %}{{ a }}{% elif b %}{{ b }}{% else %}{{ c }}{% endif %}";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("b", "YY");
        assertEquals("Hello YY", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("c", "ZZ");
        assertEquals("Hello ZZ", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("b", "YY", "c", "ZZ");
        assertEquals("Hello YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testNestedIfCondition() {
        String template = "Hello{% if a %} {{ a }}{% if b %} and {{ b }}{% endif %}{% endif %}";

        Map<String, Object> args = ImmutableMap.of();
        assertEquals("Hello", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("b", "YY");
        assertEquals("Hello", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testComment() {
        String template = "Hello {{ a }}{% comment %} and {{ b }}{% endcomment %}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX!", TemplateProcessor.process(template, args));
    }

    @Test
    public void testMapVariable() {
        String template = "Hello {{ m.a }} and {{ m.b }}!";
        Map<String, Object> nested = ImmutableMap.of("a", "XX", "b", "YY");
        Map<String, Object> args = ImmutableMap.of("m", nested);
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));
    }
}
