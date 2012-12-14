package org.yamcs.ui.eventviewer;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Vector;

import org.junit.Test;
import org.yamcs.ui.eventviewer.GlobUtils;


public class UtilsTest {

    @Test
    public void testGlobToRegExp() {
        String[] globs = {
                "ABCD",
                "*",
                "?",
                "???",
                "A?B.A",
                "*.txt",
                "*_CMD_*",
                "Dass[*]"
        };

        String[] expected = {
                "ABCD",
                ".*",
                ".",
                "...",
                "A.B[.]A",
                ".*[.]txt",
                ".*_CMD_.*",
                "Dass\\[.*\\]"
        };

        if ( globs.length != expected.length )
            fail("Wrong test setup.");

        for (int i=0; i < globs.length; ++i) {
            assertTrue(GlobUtils.globToRegExp(globs[i]).equalsIgnoreCase( expected[i] ));
        }
    }

    @Test
    public void testIsMatch() {
        String[] inputA = {
                ".*",
                "EVT_.*",
                ".*",
                "E.T_CMD.*",
                "ASW_CMD.*",

        };

        String[] inputB = {
                "Any string!!@#$",
                "EVT_CMD_ERR A.\"",
                "SSL_EVENT",
                "EXT_CMD",
                "SSL_CMD_BEGIN",
              
        };

        boolean[] expected = {
                true,
                true,
                true,
                true,
                false,
        };

        if ( inputA.length != expected.length && expected.length != inputB.length )
            fail("Wrong test setup.");

        for (int i=0; i<inputA.length; ++i) {
            assertTrue(inputA[i]+" == "+inputB[i], GlobUtils.isMatch(inputA[i], inputB[i] ) == expected[i] );
        }
    }

    @Test
    public void testGetAllMatched() {
        // (0)
        String glob = "*";
        String[] expressions = {
                "A",
                "B",
                "C"
        };

        List<String> out = GlobUtils.getAllMatched(expressions, glob);
        assertTrue(out.size() == 3);

        // (1)
        glob = "*_CMD_*";
        String[] exprs = {
                "EVT_CMD_ERR",
                "SSL_CMD_BEGIN",
                "EVT_STOP_END"
        };

        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase(exprs[0]) &&  out.get(1).equalsIgnoreCase(exprs[1]) && out.size() == 2);

        // (3)
        glob = "*_???_*";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase(exprs[0]) &&  out.get(1).equalsIgnoreCase(exprs[1]) && out.size() == 2);

        // (4)
        glob = "*_????_*";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase("EVT_STOP_END") && out.size() == 1);    

        // (5)
        glob = "???_????_*";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase("EVT_STOP_END") && out.size() == 1);

        // (6)
        glob = "*T_S*";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase("EVT_STOP_END") && out.size() == 1);

        // (7)
        glob = "*D*";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.size() == 3);   

        // (8)
        glob = "*N";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase("SSL_CMD_BEGIN") && out.size() == 1);

        // (9)
        glob = "S*N";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.get(0).equalsIgnoreCase("SSL_CMD_BEGIN") && out.size() == 1);

        // (10)
        glob = "S*_STOP_*N";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.size() == 0);

        // (11)
        glob = "?";
        out = GlobUtils.getAllMatched(exprs, glob);
        assertTrue( out.size() == 0);
    }

}
