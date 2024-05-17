package org.yamcs.xtce.xlsv7;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.xlsv7.parser.AggrMember;
import org.yamcs.xtce.xlsv7.parser.AggregateTypeParser;

public class AggregateTypeParserTest {
    @Test
    public void test1() throws ParseException {
        String input = "{ @description(\"Description of member1\") uint8 member1; @description(\"Description of member2\") uint16 member2; string member3}";
        AggregateTypeParser parser = new AggregateTypeParser(new StringReader(input));
        List<AggrMember> l = parser.parse();
        assertEquals(3, l.size());

        assertEquals("member1", l.get(0).name());
        assertEquals("uint8", l.get(0).dataType());
        assertEquals("Description of member1", l.get(0).description());

        assertEquals("member2", l.get(1).name());
        assertEquals("uint16", l.get(1).dataType());
        assertEquals("Description of member2", l.get(1).description());

        assertEquals("member3", l.get(2).name());
        assertEquals("string", l.get(2).dataType());
        assertEquals(null, l.get(2).description());   
    }
    
    @Test
    public void test2() throws Exception {
        String input="{\n"
                + "   uint8 member1;\n"
                + "   uint16 member2;\n"
                + "   float32 member3; \n"
                + "}";
        
        AggregateTypeParser parser = new AggregateTypeParser(new StringReader(input));
        List<AggrMember> l = parser.parse();
        assertEquals(3, l.size());
        
    }
    
    @Test
    public void test3() throws Exception {
        String input = "{\n"
                + "   uint8 member1;\n"
                + "  @description(”a description for member 2”)\n"
                + "   CALIB_TC_p4 member2;\n"
                + "}";
        
        AggregateTypeParser parser = new AggregateTypeParser(new StringReader(input));
        List<AggrMember> l = parser.parse();
        assertEquals(2, l.size());
        assertEquals("a description for member 2", l.get(1).description());
    }
}
