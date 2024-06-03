package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.mdb.Mdb;

public class XtceToGpbAssemblerTest {

    @Test
    public void toCommandInfo_float_test() throws Exception {
        // Arrange
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        Mdb mdb = MdbFactory.getInstance("refmdb");
        MetaCommand cmd1 = mdb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");

        // Act
        CommandInfo commandInfo = XtceToGpbAssembler.toCommandInfo(cmd1, DetailLevel.FULL);

        // Assert
        assertEquals("FLOAT_ARG_TC", commandInfo.getName());
        assertEquals("float", commandInfo.getArgument(0).getType().getEngType());
        assertEquals(-30, commandInfo.getArgument(0).getType().getRangeMin(), 0);
        assertEquals(-10, commandInfo.getArgument(0).getType().getRangeMax(), 0);
        assertEquals("m/s", commandInfo.getArgument(0).getType().getUnitSet(0).getUnit());
    }

    @Test
    public void toCommandInfo_int_test() throws Exception {
        // Arrange
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        Mdb mdb = MdbFactory.getInstance("refmdb");
        MetaCommand cmd1 = mdb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");

        // Act
        CommandInfo commandInfo = XtceToGpbAssembler.toCommandInfo(cmd1, DetailLevel.FULL);

        // Assert
        assertEquals("CCSDS_TC", commandInfo.getName());
        assertEquals("integer", commandInfo.getArgument(0).getType().getEngType());
        assertTrue(commandInfo.getArgument(0).getType().hasRangeMin(), "should have a range set");
        assertEquals(1, commandInfo.getArgument(0).getType().getRangeMin(), 0);
        assertEquals(3, commandInfo.getArgument(0).getType().getRangeMax(), 0);
    }

    @Test
    public void toCommandInfo_calib_test() throws Exception {
        // Arrange
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        Mdb mdb = MdbFactory.getInstance("refmdb");
        MetaCommand cmd1 = mdb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");

        // Act
        CommandInfo commandInfo = XtceToGpbAssembler.toCommandInfo(cmd1, DetailLevel.FULL);

        // Assert
        assertEquals("CALIB_TC", commandInfo.getName());
        assertEquals("enumeration", commandInfo.getArgument(3).getType().getEngType());
        assertEquals("value0", commandInfo.getArgument(3).getType().getEnumValue(0).getLabel());
        assertEquals("value2", commandInfo.getArgument(3).getType().getEnumValue(2).getLabel());
        assertTrue(!commandInfo.getArgument(0).getType().hasRangeMin(), "should not have a range set");
    }
}
