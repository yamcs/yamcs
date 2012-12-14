package org.yamcs.parser;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;

import org.junit.Test;
import org.yamcs.commanding.TcParameterDefinition.SwTypes;
import org.yamcs.parser.HlclCommandParser;
import org.yamcs.parser.HlclParsedCommand;
import org.yamcs.parser.HlclParsedParameter;
import org.yamcs.parser.ParseException;


/**
 * tests the parsinf of hlcl commands
 * @author mache
 *
 */
public class HlclCommandParserTest {

    @Test
    public void testCmdString() throws ParseException {
		String s="EDR_Cmd_RIC_ECM_MEDIAINFO (\"nice: \"\"str\"\"ing\",\n"+
                "PCTS_ECMFC        : 146,\n"+
                "PCTS_MEDIATYPE    : 2,\n"+
                "PCTS_LANIPADR     : 16#40A86400#,\n"+
                "PCTS_LANPORT      : 16#FE02#,\n"+
                "PCTS_SERCHANNELID : \"16#0200#\",\n"+
                "PCTS_SERBITRATE   : 19200,\n" +
                "TEST_STATECODE    : $SU3)";
		HlclCommandParser parser=new HlclCommandParser(new StringReader(s));
		HlclParsedCommand cmd=parser.CmdString();
		assertEquals("EDR_Cmd_RIC_ECM_MEDIAINFO",cmd.commandName);
		ArrayList<HlclParsedParameter> pl=cmd.parameterList;
		assertEquals(8,pl.size());
		assertEquals(null,pl.get(0).name);
		assertEquals("PCTS_ECMFC",pl.get(1).name);
		assertEquals("PCTS_MEDIATYPE",pl.get(2).name);
		assertEquals("PCTS_LANIPADR",pl.get(3).name);
		assertEquals("PCTS_LANPORT",pl.get(4).name);
		assertEquals("PCTS_SERCHANNELID",pl.get(5).name);
		assertEquals("PCTS_SERBITRATE",pl.get(6).name);
		assertEquals("TEST_STATECODE",pl.get(7).name);
		
		assertEquals("nice: \"str\"ing", new String((byte[])pl.get(0).value));
		assertEquals(146,(long)(Long)pl.get(1).value);
		assertEquals(2,(long)(Long) pl.get(2).value);
		assertEquals(1084777472,(long)(Long)pl.get(3).value);
        assertEquals(65026,(long)(Long)pl.get(4).value);
		assertEquals("16#0200#",new String((byte[])pl.get(5).value));
		assertEquals(SwTypes.STATE_CODE_TYPE,pl.get(7).type);
		assertEquals("SU3",(String)pl.get(7).value);
		
		s="TEST_CMD";
		parser.ReInit(new StringReader(s));
		cmd=parser.CmdString();
		assertEquals("TEST_CMD",cmd.commandName);
		assertEquals(null,cmd.parameterList);
		
		s="TEST1_CMD()";
		parser.ReInit(new StringReader(s));
		cmd=parser.CmdString();
		assertEquals("TEST1_CMD",cmd.commandName);
		assertEquals(null,cmd.parameterList);
	}

}
