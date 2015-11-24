package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import com.sun.deploy.util.ArrayUtil;
import junit.framework.Assert;

import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by msc on 27/05/15.
 */
public class TestXtceCommandEncoding {
    @Test
    public void floatCommand() throws ErrorInCommand {
        // encode command
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("float_arg", "-30");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("double_arg", "1");
        arguments.add(argumentAssignment2);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        Assert.assertTrue(b[0] != 0);
    }

    @Test
    public void stringCommand() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/STRING_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("string_arg", "aaaa");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("terminatedString_arg", "bbbb");
        arguments.add(argumentAssignment2);
        ArgumentAssignment argumentAssignment3 = new ArgumentAssignment("prependedSizeString_arg", "cccc");
        arguments.add(argumentAssignment3);
        ArgumentAssignment argumentAssignment4 = new ArgumentAssignment("fixedString_arg", "dddd");
        arguments.add(argumentAssignment4);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                97, 97, 97, 97, 0,              // aaaa
                97, 98, 99, 100, 101, 102, 0,   // abcdef
                98, 98, 98, 98, 0x2C,           // aaaa
                0, 4, 99, 99, 99, 99,           // bbbb
                100, 100, 100, 100, 0, 0, 0     // dddd
        };
        Assert.assertTrue(Arrays.equals(b, expectedResult));
    }
    
    @Test
    public void littleEndianUint() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/LE_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("p2", "0x12");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        System.out.println("command buffer: "+StringConvertors.arrayToHexString(b));
        assertEquals(0x0A0B, bb.getShort());
        assertEquals(0x12, bb.getShort());
    }
    
    @Test
    public void booleanCommandTrue() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "true");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertEquals(1<<7, b[0]&0xFF);
        
        
    }
    
    @Test
    public void booleanCommandFalse() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "false");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertEquals(0, b[0]&0xFF);
        
        
    }
}
