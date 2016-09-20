package org.yamcs.web.rest.mdb;

import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Mdb;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
/**
 * Created by msc on 05.04.16.
 */
public class XtceToGpbAssemblerTest {

    @Test
    public void toCommandInfo_float_test() throws  Exception
    {
        // Arrange
        XtceToGpbAssembler target = new XtceToGpbAssembler();
        Set<RestRequest.Option> optionSet=new HashSet<>();

        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
        XtceDb db = XtceDbFactory.getInstance("refmdb");
        MetaCommand cmd1 = db.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");

        // Act
        Mdb.CommandInfo commandInfo = target.toCommandInfo(cmd1, "url", XtceToGpbAssembler.DetailLevel.FULL, optionSet);

        // Assert
        assertEquals("FLOAT_ARG_TC", commandInfo.getName());
        assertEquals("float", commandInfo.getArgument(0).getType().getEngType());
        assertEquals(-30, commandInfo.getArgument(0).getType().getRangeMin(), 0);
        assertEquals(-10, commandInfo.getArgument(0).getType().getRangeMax(), 0);
        assertEquals("m/s", commandInfo.getArgument(0).getType().getUnitSet(0).getUnit());
    }

    @Test
    public void toCommandInfo_int_test() throws  Exception
    {
        // Arrange
        XtceToGpbAssembler target = new XtceToGpbAssembler();
        Set<RestRequest.Option> optionSet=new HashSet<>();

        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
        XtceDb db = XtceDbFactory.getInstance("refmdb");
        MetaCommand cmd1 = db.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");

        // Act
        Mdb.CommandInfo commandInfo = target.toCommandInfo(cmd1, "url", XtceToGpbAssembler.DetailLevel.FULL, optionSet);

        // Assert
        assertEquals("CCSDS_TC", commandInfo.getName());
        assertEquals("integer", commandInfo.getArgument(0).getType().getEngType());
        assertTrue("should have a range set", commandInfo.getArgument(0).getType().hasRangeMin());
        assertEquals(1, commandInfo.getArgument(0).getType().getRangeMin(), 0);
        assertEquals(3, commandInfo.getArgument(0).getType().getRangeMax(), 0);
    }

    @Test
    public void toCommandInfo_calib_test() throws  Exception
    {
        // Arrange
        XtceToGpbAssembler target = new XtceToGpbAssembler();
        Set<RestRequest.Option> optionSet=new HashSet<>();

        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
        XtceDb db = XtceDbFactory.getInstance("refmdb");
        MetaCommand cmd1 = db.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");

        // Act
        Mdb.CommandInfo commandInfo = target.toCommandInfo(cmd1, "url", XtceToGpbAssembler.DetailLevel.FULL, optionSet);

        // Assert
        assertEquals("CALIB_TC", commandInfo.getName());
        assertEquals("enumeration", commandInfo.getArgument(3).getType().getEngType());
        assertEquals("value0", commandInfo.getArgument(3).getType().getEnumValue(0).getLabel());
        assertEquals("value2", commandInfo.getArgument(3).getType().getEnumValue(2).getLabel());
        assertTrue("should not have a range set", !commandInfo.getArgument(0).getType().hasRangeMin());
    }


}
