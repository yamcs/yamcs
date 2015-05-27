package org.yamcs.xtceproc;

import junit.framework.Assert;
import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.YProcessor;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import java.nio.ByteBuffer;
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
}
