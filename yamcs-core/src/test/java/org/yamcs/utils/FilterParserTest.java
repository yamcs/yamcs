package org.yamcs.utils;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;

import org.junit.Test;
import org.yamcs.utils.parser.FilterParser;

public class FilterParserTest {
    @Test
    public void test1() throws Exception {
        FilterParser fp = new FilterParser(new StringReader("a=b"));
        FilterParser.Result r = fp.parse();
        assertEquals("a", r.key);
        assertEquals(FilterParser.Operator.EQUAL, r.op);
        assertEquals("b", r.value);
     
        fp.ReInit(new StringReader("\"cucu\" != bau"));
        r = fp.parse();
        assertEquals("cucu", r.key);
        assertEquals(FilterParser.Operator.NOT_EQUAL, r.op);
        assertEquals("bau", r.value);
     
        
        fp.ReInit(new StringReader("tag:cucu=bau"));
        r = fp.parse();
        assertEquals("tag:cucu", r.key);
        assertEquals(FilterParser.Operator.EQUAL, r.op);
        assertEquals("bau", r.value);
        
    }
}
