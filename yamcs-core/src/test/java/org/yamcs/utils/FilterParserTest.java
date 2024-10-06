package org.yamcs.utils;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.IncorrectTypeException;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;

public class FilterParserTest {

    private static final HexFormat HEX = HexFormat.of();

    private Item a = new Item("round horn", EventSeverity.INFO, false, 1, HEX.parseHex("aabb"));
    private Item b = new Item("wacky hippo", EventSeverity.WATCH, true, 2, HEX.parseHex("bbcc"));
    private Item c = new Item("icy wombat", EventSeverity.WARNING, true, 3, HEX.parseHex("ccdd"));
    private Item d = new Item("lush ghost", EventSeverity.DISTRESS, false, 4, HEX.parseHex("ddee"));
    private Item e = new Item("rich sea", EventSeverity.CRITICAL, false, 5, HEX.parseHex("eeff"));
    private Item f = new Item("heavy lake", null, false, 6, HEX.parseHex("ff00"));

    private List<Item> allItems = asList(a, b, c, d, e, f);

    private List<Item> filterItems(ItemFilter filter) {
        return allItems.stream().filter(filter::matches).toList();
    }

    @Test
    public void testEmptyFilter() throws ParseException {
        var filter = new ItemFilter("");
        assertEquals(allItems, filterItems(filter));

        filter = new ItemFilter("\n");
        assertEquals(allItems, filterItems(filter));
    }

    @Test
    public void testRegex() throws ParseException {
        var filter = new ItemFilter("name =~ \"s.a\"");
        assertEquals(asList(e), filterItems(filter));

        filter = new ItemFilter("name =~ \"^r.*$\"");
        assertEquals(asList(a, e), filterItems(filter));

        filter = new ItemFilter("name !~ \"^r.*$\"");
        assertEquals(asList(b, c, d, f), filterItems(filter));

        filter = new ItemFilter("name =~ \"SeA\"");
        assertEquals(asList(), filterItems(filter), "Regex is case-sensitive");

        filter = new ItemFilter("name =~ \"(?i)SeA\""); // Case-insensitive
        assertEquals(asList(e), filterItems(filter));
    }

    @Test
    public void testNullField() throws ParseException {
        var filter = new ItemFilter("severity=null");
        assertEquals(asList(f), filterItems(filter));

        var nullString = new Item(null, EventSeverity.INFO, false, 1, null);
        var extendedItems = new ArrayList<>(allItems);
        extendedItems.add(nullString);
        filter = new ItemFilter("name = null");
        assertEquals(asList(nullString), extendedItems.stream().filter(filter::matches).toList());

        var nullBoolean = new Item("swift gopher", EventSeverity.INFO, null, 1, null);
        extendedItems = new ArrayList<>(allItems);
        extendedItems.add(nullBoolean);
        filter = new ItemFilter("animal = null");
        assertEquals(asList(nullBoolean), extendedItems.stream().filter(filter::matches).toList());

        var nullNumber = new Item("swift gopher", EventSeverity.INFO, true, null, null);
        extendedItems = new ArrayList<>(allItems);
        extendedItems.add(nullNumber);
        filter = new ItemFilter("order = null");
        assertEquals(asList(nullNumber), extendedItems.stream().filter(filter::matches).toList());

        var nullBinary = new Item("swift gopher", EventSeverity.INFO, true, 1, null);
        extendedItems = new ArrayList<>(allItems);
        extendedItems.add(nullBinary);
        filter = new ItemFilter("binary = null");
        assertEquals(asList(nullBinary), extendedItems.stream().filter(filter::matches).toList());
    }

    @Test
    public void testPrecendence() throws ParseException {
        var filter = new ItemFilter("lush AND (ghost OR wombat)");
        assertEquals(asList(d), filterItems(filter));

        filter = new ItemFilter("(lush AND ghost) OR wombat");
        assertEquals(asList(c, d), filterItems(filter));

        // Unlike programming languages, OR has higher precedence than AND
        filter = new ItemFilter("lush AND ghost OR wombat");
        assertEquals(asList(d), filterItems(filter));
    }

    @Test
    public void testLogicalOperators() throws ParseException {
        var filter = new ItemFilter("name=\"icy wombat\" OR severity=DISTRESS");
        assertEquals(asList(c, d), filterItems(filter));

        filter = new ItemFilter("name=\"icy wombat\" AND severity=DISTRESS");
        assertEquals(asList(), filterItems(filter));

        filter = new ItemFilter("name=\"icy wombat\" AND severity=WARNING");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("name=\"icy wombat\" AND (severity=WARNING OR severity=DISTRESS)");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("name=\"icy wombat\" OR (severity=WARNING OR severity=DISTRESS)");
        assertEquals(asList(c, d), filterItems(filter));

        filter = new ItemFilter("(name=\"icy wombat\" OR severity=CRITICAL) OR severity=DISTRESS");
        assertEquals(asList(c, d, e), filterItems(filter));
    }

    @Test
    public void testQuotedString() throws ParseException {
        var filter = new ItemFilter("name=\"icy wombat\"");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("name != \"icy wombat\"");
        assertEquals(asList(a, b, d, e, f), filterItems(filter));

        filter = new ItemFilter("-name = \"icy wombat\"");
        assertEquals(asList(a, b, d, e, f), filterItems(filter));

        filter = new ItemFilter("NOT ( name = \"icy wombat\" )");
        assertEquals(asList(a, b, d, e, f), filterItems(filter));

        filter = new ItemFilter("name > \"mmm\"");
        assertEquals(asList(a, b, e), filterItems(filter));
    }

    @Test
    public void testEnum() throws ParseException {
        var filter = new ItemFilter("severity = INFO");
        assertEquals(asList(a), filterItems(filter));

        filter = new ItemFilter("severity = info");
        assertEquals(asList(a), filterItems(filter), "Must be case-insensitive");

        filter = new ItemFilter("severity >= DISTRESS");
        assertEquals(asList(d, e), filterItems(filter));

        var exc = assertThrows(IncorrectTypeException.class, () -> {
            new ItemFilter("severity >= INFOooo");
        });
        assertEquals(exc.getValue(), "INFOooo");

        var exc2 = assertThrows(UnknownFieldException.class, () -> {
            new ItemFilter("severityy >= INFO");
        });
        assertEquals(exc2.getField(), "severityy");
    }

    @Test
    public void testBoolean() throws ParseException {
        var filter = new ItemFilter("animal=true");
        assertEquals(asList(b, c), filterItems(filter));

        filter = new ItemFilter("animal=True");
        assertEquals(asList(b, c), filterItems(filter));

        filter = new ItemFilter("animal=false");
        assertEquals(asList(a, d, e, f), filterItems(filter));

        filter = new ItemFilter("animal=False");
        assertEquals(asList(a, d, e, f), filterItems(filter));
    }

    @Test
    public void testNumber() throws ParseException {
        var filter = new ItemFilter("order < \"3.2\"");
        assertEquals(asList(a, b, c), filterItems(filter));
    }

    @Test
    public void testBinary() throws ParseException {
        var filter = new ItemFilter("binary=ccdd");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("binary=CCDD");
        assertEquals(asList(c), filterItems(filter), "Case-insensitive");

        filter = new ItemFilter("binary=\"ccdd\"");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("binary:cc");
        assertEquals(asList(b, c), filterItems(filter));

        filter = new ItemFilter("binary < cc");
        assertEquals(asList(a, b), filterItems(filter));
    }

    @Test
    public void testQuotedEnum() throws ParseException {
        var filter = new ItemFilter("severity = \"INFO\"");
        assertEquals(asList(a), filterItems(filter));

        filter = new ItemFilter("severity >= \"DISTRESS\"");
        assertEquals(asList(d, e), filterItems(filter));

        var exc = assertThrows(IncorrectTypeException.class, () -> {
            new ItemFilter("severity >= \"INFOooo\"");
        });
        assertEquals(exc.getValue(), "INFOooo");

        var exc2 = assertThrows(UnknownFieldException.class, () -> {
            new ItemFilter("severityy >= \"INFO\"");
        });
        assertEquals(exc2.getField(), "severityy");
    }

    @Test
    public void testTextSearch() throws ParseException {
        var filter = new ItemFilter("wombat");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("hippo OR wombat");
        assertEquals(asList(b, c), filterItems(filter));

        filter = new ItemFilter("-hippo");
        assertEquals(asList(a, c, d, e, f), filterItems(filter));

        filter = new ItemFilter("-wombat");
        assertEquals(asList(a, b, d, e, f), filterItems(filter));

        filter = new ItemFilter("-wombat AND -hippo");
        assertEquals(asList(a, d, e, f), filterItems(filter));

        filter = new ItemFilter("NOT wombat AND NOT hippo");
        assertEquals(asList(a, d, e, f), filterItems(filter));

        filter = new ItemFilter("wom AND bat");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("wom bat");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("wom -bat");
        assertEquals(asList(), filterItems(filter));

        assertThrows(ParseException.class, () -> {
            new ItemFilter("- wombat");
        }, "No space allowed after minus sign");
    }

    @Test
    public void testCaseSensitiveLogicalOperators() throws ParseException {
        var filter = new ItemFilter("wombat OR hippo");
        assertEquals(asList(b, c), filterItems(filter));

        filter = new ItemFilter("wombat or hippo"); // Same as: wombat AND or AND hippo
        assertEquals(asList(), filterItems(filter));
    }

    @Test
    public void testMultilineAndComments() throws ParseException {
        var filter = new ItemFilter("""
                -wombat
                wombat
                """);
        assertEquals(asList(), filterItems(filter));

        filter = new ItemFilter("""
                ---wombat
                wombat
                """);
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("""
                -wombat
                --wombat
                """);
        assertEquals(asList(a, b, d, e, f), filterItems(filter));

        // Intentional EOF instead of newline, following final line comment
        filter = new ItemFilter("""
                --nothing
                --but
                --comments""");
        assertEquals(allItems, filterItems(filter));
    }

    @Test
    public void testHas() throws ParseException {
        var filter = new ItemFilter("name:wom");
        assertEquals(asList(c), filterItems(filter));
    }

    @Test
    public void testCaseInsensitiveFields() throws ParseException {
        var filter = new ItemFilter("name:wom");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("nAme:wom");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("NAME:wom");
        assertEquals(asList(c), filterItems(filter));
    }

    @Test
    public void testCaseInsensitiveMatching() throws ParseException {
        var filter = new ItemFilter("name:wom");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("name:wOm");
        assertEquals(asList(c), filterItems(filter));

        filter = new ItemFilter("wOmBat");
        assertEquals(asList(c), filterItems(filter));
    }

    @Test
    public void testPrefix() throws ParseException {
        var filter = new ItemFilter("label.name=\"icy wombat\"");
        assertEquals(asList(c), filterItems(filter));
    }

    public static record Item(String name, EventSeverity severity, Boolean animal, Integer order, byte[] binary) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static class ItemFilter extends Filter<Item> {

        public ItemFilter(String query) throws ParseException {
            super(query);
            addStringField("name", item -> item.name());
            addEnumField("severity", EventSeverity.class, item -> item.severity());
            addBooleanField("animal", item -> item.animal());
            addNumberField("order", item -> item.order());
            addBinaryField("binary", item -> item.binary());
            addPrefixField("label.", (item, field) -> {
                if (field.equals("label.name")) {
                    return item.name;
                } else {
                    return null;
                }
            });
            parse();
        }

        @Override
        protected boolean matchesLiteral(Item item, String literal) {
            return item.name().toLowerCase().contains(literal);
        }
    }
}
