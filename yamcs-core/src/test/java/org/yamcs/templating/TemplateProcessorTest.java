package org.yamcs.templating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

public class TemplateProcessorTest {

    @Test
    public void testProcess() throws ParseException {
        String template = "Hello {{ a }} and {{b}}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));
    }

    @Test
    public void testUnsetVariable() {
        assertThrows(IllegalArgumentException.class, () -> {
            String template = "Hello {{ a }} and {{b}}!";
            Map<String, Object> args = ImmutableMap.of("a", "XX");
            TemplateProcessor.process(template, args);
        });
    }

    @Test
    public void testIfCondition() throws ParseException {
        String template = "Hello {{ a }}{% if b %} and {{ b }}{% endif %}";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testIfElseCondition() throws ParseException {
        String template = "Hello {% if a %}{{ a }}{% else %}{{ b }}{% endif %}";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("b", "YY");
        assertEquals("Hello YY", TemplateProcessor.process(template, args));
    }

    @Test
    public void testIfElifElseCondition() throws ParseException {
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
    public void testNestedIfCondition() throws ParseException {
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
    public void testComment() throws ParseException {
        String template = "Hello {{ a }}{% comment %} and {{ b }}{% endcomment %}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertEquals("Hello XX!", TemplateProcessor.process(template, args));
    }

    @Test
    public void testMapVariable() throws ParseException {
        String template = "Hello {{ m.a }} and {{ m.b }}!";
        Map<String, Object> nested = ImmutableMap.of("a", "XX", "b", "YY");
        Map<String, Object> args = ImmutableMap.of("m", nested);
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));
    }

    @Test
    public void testSingleCurlyBracket() throws ParseException {
        String template = "{ \"abc\": 1234 }";
        Map<String, Object> args = ImmutableMap.of();
        assertEquals(template, TemplateProcessor.process(template, args));
    }

    @Test
    public void testEscapeFilter() throws ParseException {
        String template = "Hello {{ a|escape }} and {{b | escape}}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX", "b", "YY");
        assertEquals("Hello XX and YY!", TemplateProcessor.process(template, args));

        args = ImmutableMap.of("a", "X<strong>X</strong>", "b", "Y&Y");
        assertEquals("Hello X&lt;strong&gt;X&lt;/strong&gt; and Y&amp;Y!",
                TemplateProcessor.process(template, args));
    }

    @Test
    public void testUnknownFilter() throws ParseException {
        String template = "Hello {{ a|foo }}!";
        Map<String, Object> args = ImmutableMap.of("a", "XX");
        assertThrows(IllegalArgumentException.class, () -> {
            TemplateProcessor.process(template, args);
        });
    }
}
