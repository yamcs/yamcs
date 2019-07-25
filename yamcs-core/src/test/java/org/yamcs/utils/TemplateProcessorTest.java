package org.yamcs.utils;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class TemplateProcessorTest {

    @Test
    public void testProcess() {
        String template = "Hello {{ a }} and {{b}}!";
        Map<String, String> args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsetVariable() {
        String template = "Hello {{ a }} and {{b}}!";
        Map<String, String> args = ImmutableMap.of("a", "XX");
        TemplateProcessor.process(template, args);
    }

    @Test
    public void testIfCondition() {
        String template = "Hello {{ a }}{% if b %} and {{ b }}{% endif %}";
        Map<String, String> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testNestedIfCondition() {
        String template = "Hello{% if a %} {{ a }}{% if b %} and {{ b }}{% endif %}{% endif %}";

        Map<String, String> args = ImmutableMap.of();
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
        Map<String, String> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX!", TemplateProcessor.process(template, args));
    }
}
