package org.yamcs.tse;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.SpaceSystemLoader;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;

public class TseLoader implements SpaceSystemLoader {

    public static final String CMD_COMMAND = "COMMAND";
    public static final String CMD_QUERY = "QUERY";

    public static final String ARG_TYPE = "type";
    public static final String ARG_COMMAND = "command";
    public static final String ARG_RESPONSE = "response";

    private String name;

    public TseLoader() {
        this(Collections.emptyMap());
    }

    public TseLoader(Map<String, Object> config) {
        name = YConfiguration.getString(config, "name", "TSE");
    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        return true;
    }

    @Override
    public String getConfigName() throws ConfigurationException {
        return name;
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
        // we want to load all the time the file as fresh so no consistency date
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        SpaceSystem ss = new SpaceSystem(name);
        ss.setShortDescription("Test Support Equipment");

        MetaCommand tc = createTC();
        ss.addMetaCommand(tc);
        ss.addCommandContainer(tc.getCommandContainer());

        MetaCommand command = createCOMMAND();
        command.setBaseMetaCommand(tc);
        ss.addMetaCommand(command);
        ss.addCommandContainer(command.getCommandContainer());

        MetaCommand query = createQUERY();
        query.setBaseMetaCommand(tc);
        ss.addMetaCommand(query);
        ss.addCommandContainer(query.getCommandContainer());

        return ss;
    }

    private MetaCommand createTC() {
        CommandContainer container = new CommandContainer("TC");
        MetaCommand command = new MetaCommand("TC");
        command.setCommandContainer(container);
        command.setAbstract(true);

        Argument typeArgument = new Argument(ARG_TYPE);
        IntegerArgumentType.Builder typeArgumentTypeBuilder = new IntegerArgumentType.Builder().setName(ARG_TYPE);
        IntegerDataEncoding.Builder typeArgumentEncoding = new IntegerDataEncoding.Builder().setSizeInBits(8);
        typeArgumentTypeBuilder.setEncoding(typeArgumentEncoding);
        typeArgumentTypeBuilder.setSizeInBits(8);
        typeArgumentTypeBuilder.setSigned(false);
        typeArgument.setArgumentType(typeArgumentTypeBuilder.build());
        command.addArgument(typeArgument);

        ArgumentEntry typeArgumentEntry = new ArgumentEntry(0, ReferenceLocationType.CONTAINER_START,
                typeArgument);
        container.addEntry(typeArgumentEntry);

        Argument commandArgument = new Argument(ARG_COMMAND);
        StringArgumentType.Builder commandArgumentType = new StringArgumentType.Builder().setName(ARG_COMMAND);
        StringDataEncoding.Builder commandArgumentEncoding = new StringDataEncoding.Builder()
                .setSizeType(SizeType.TERMINATION_CHAR);
        commandArgumentEncoding.setTerminationChar((byte) 0x00);
        commandArgumentType.setEncoding(commandArgumentEncoding);
        commandArgument.setArgumentType(commandArgumentType.build());
        command.addArgument(commandArgument);

        ArgumentEntry commandArgumentEntry = new ArgumentEntry(8, ReferenceLocationType.CONTAINER_START,
                commandArgument);
        container.addEntry(commandArgumentEntry);

        return command;
    }

    private MetaCommand createCOMMAND() {
        CommandContainer container = new CommandContainer(CMD_COMMAND);
        MetaCommand command = new MetaCommand(CMD_COMMAND);
        command.setCommandContainer(container);
        command.setAbstract(true);

        ArgumentAssignment assignment = new ArgumentAssignment(ARG_TYPE, "0");
        command.addArgumentAssignment(assignment);

        return command;
    }

    private MetaCommand createQUERY() {
        CommandContainer container = new CommandContainer(CMD_QUERY);
        MetaCommand command = new MetaCommand(CMD_QUERY);
        command.setCommandContainer(container);
        command.setAbstract(true);

        ArgumentAssignment assignment = new ArgumentAssignment(ARG_TYPE, "1");
        command.addArgumentAssignment(assignment);

        Argument responseArgument = new Argument(ARG_RESPONSE);
        StringArgumentType.Builder responseArgumentType = new StringArgumentType.Builder().setName(ARG_RESPONSE);
        StringDataEncoding.Builder responseArgumentEncoding = new StringDataEncoding.Builder()
                .setSizeType(SizeType.TERMINATION_CHAR);
        responseArgumentEncoding.setTerminationChar((byte) 0x00);
        responseArgumentType.setEncoding(responseArgumentEncoding);
        responseArgument.setArgumentType(responseArgumentType.build());
        command.addArgument(responseArgument);

        ArgumentEntry responseArgumentEntry = new ArgumentEntry(responseArgument);
        container.addEntry(responseArgumentEntry);

        return command;
    }
}
