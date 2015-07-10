package org.yamcs.xtceproc;

import static org.junit.Assert.*;
import junit.framework.Assert;

import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments);

        Assert.assertTrue(b[0] != 0);
    }

    @Test
    public void stringCommand() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/STRING_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("string_arg", "aaaa");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments);

        Assert.assertTrue(b[0] != 0);
    }
    
    @Test
    public void littleEndianUint() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/LE_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("p2", "0x12");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments);
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
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments);

        assertEquals(1<<7, b[0]&0xFF);
        
        
    }
    
    @Test
    public void booleanCommandFalse() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "false");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments);

        assertEquals(0, b[0]&0xFF);
        
        
    }
}
