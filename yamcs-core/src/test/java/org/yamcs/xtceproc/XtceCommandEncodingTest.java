package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import junit.framework.Assert;

/**
 * Created by msc on 27/05/15.
 */
public class XtceCommandEncodingTest {
    
    @Test
    public void floatCommand() throws ErrorInCommand {
        // encode command
        XtceDb xtcedb=XtceDbFactory.createInstance("refmdb");
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
    public void floatCommandDefault()
    {
        // encode command
        XtceDb xtcedb=XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        boolean errorInCommand = false;

        try {
            // should complain that parameter has not been assigned
            List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>();
            byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        }
        catch (ErrorInCommand e)
        {
            errorInCommand = true;
        }

        assertTrue(errorInCommand);
    }

    @Test
    public void stringCommand() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
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
                100, 100, 100, 100, 0, 0     // dddd
        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }
    @Test
    public void cgsLikeStringCommand() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CGS_LIKE_STRING_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("string_arg1", "aaaa");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("string_arg2", "bbbb");
        arguments.add(argumentAssignment2);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                0, 4, 97, 97, 97, 97, 0, 0,   // aaaa
                98, 98, 98, 98, 0x2C, 0       // bbbb
                
        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }
    
    @Test
    public void binaryCommand() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BINARY_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("binary_arg1", "0102");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("binary_arg2", "0A1B");
        arguments.add(argumentAssignment2);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                0x01, 0x02, 0, 0, 0, 
                0x0A, 0x1B, 0, 0, 0, 0    
                
        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }
    @Test
    public void littleEndianUint() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/LE_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("p2", "0x12");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        System.out.println("command buffer: "+StringConverter.arrayToHexString(b));
        assertEquals(0x0A0B, bb.getShort());
        assertEquals(0x12, bb.getShort());
    }
    
    @Test
    public void booleanCommandTrue() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "true");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertEquals(0b11000000, b[0]&0xFF);
    }
    
    @Test
    public void booleanCommandFalseTrue() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "false");
        arguments.add(argumentAssignment1);
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        // - assigned false
        // - default argument assignemnt true
        assertEquals(0b01000000, b[0]&0xFF);

        
    }
   
    @Test
    public void int64CommandArgumentRange() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");
        
        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060707", "p2", "0xF102030405060708", "p3", "-18374120213919168760");
            MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p1"));
        }
        
        
        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF10203040506070A", "p3", "-18374120213919168760");
            MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p2"));
        }
        
        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF102030405060708", "p3", "-0X0102030405060707");
            MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p3"));
        }
    }

    @Test
    public void int64CommandArgumentEncoding() throws ErrorInCommand {
        XtceDb xtcedb = XtceDbFactory.createInstance("refmdb");
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");
        List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF102030405060708", "p3", "-0X0102030405060708");
        byte[] b = MetaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals("0102030405060708F102030405060708FEFDFCFBFAF9F8F8", StringConverter.arrayToHexString(b));
    }

    private List<ArgumentAssignment> getArgAssignment(String ...v) {
        if((v.length&0x1)!=0) throw new IllegalArgumentException("Please pass an even number of arguments: arg1,value1,arg2,value2...");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        for(int i=0;i<v.length;i+=2) {
            ArgumentAssignment arg = new ArgumentAssignment(v[i], v[i+1]);
            arguments.add(arg);
        }
        return arguments;
    }
}
