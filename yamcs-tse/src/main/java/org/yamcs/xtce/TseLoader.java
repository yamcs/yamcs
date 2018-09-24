package org.yamcs.xtce;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.StringDataEncoding.SizeType;

public class TseLoader implements SpaceSystemLoader {

    public static final String CMD_COMMAND = "COMMAND";
    public static final String CMD_QUERY = "QUERY";

    public static final String ARG_TYPE = "type";
    public static final String ARG_COMMAND = "command";
    public static final String ARG_RESPONSE = "response";

    private String name;

    public TseLoader(String spec) {
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
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        SpaceSystem ss = new SpaceSystem(name);
        ss.setShortDescription("Test Support Equipment");

        MetaCommand tc = createTC();
        ss.addMetaCommand(tc);

        MetaCommand command = createCOMMAND();
        command.setBaseMetaCommand(tc);
        ss.addMetaCommand(command);

        MetaCommand query = createQUERY();
        query.setBaseMetaCommand(tc);
        ss.addMetaCommand(query);

        return ss;
    }

    private MetaCommand createTC() {
        CommandContainer container = new CommandContainer("TC");
        MetaCommand command = new MetaCommand("TC");
        command.setCommandContainer(container);
        command.setAbstract(true);

        Argument typeArgument = new Argument("type");
        IntegerArgumentType typeArgumentType = new IntegerArgumentType("type");
        IntegerDataEncoding typeArgumentEncoding = new IntegerDataEncoding(8);
        typeArgumentType.setEncoding(typeArgumentEncoding);
        typeArgumentType.setSizeInBits(8);
        typeArgumentType.setSigned(false);
        typeArgument.setArgumentType(typeArgumentType);
        command.addArgument(typeArgument);

        ArgumentEntry typeArgumentEntry = new ArgumentEntry(0, ReferenceLocationType.containerStart,
                typeArgument);
        container.addEntry(typeArgumentEntry);

        Argument commandArgument = new Argument("command");
        StringArgumentType commandArgumentType = new StringArgumentType("command");
        StringDataEncoding commandArgumentEncoding = new StringDataEncoding(SizeType.TERMINATION_CHAR);
        commandArgumentEncoding.setTerminationChar((byte) 0x00);
        commandArgumentType.setEncoding(commandArgumentEncoding);
        commandArgument.setArgumentType(commandArgumentType);
        command.addArgument(commandArgument);

        ArgumentEntry commandArgumentEntry = new ArgumentEntry(8, ReferenceLocationType.containerStart,
                commandArgument);
        container.addEntry(commandArgumentEntry);

        return command;
    }

    private MetaCommand createCOMMAND() {
        CommandContainer container = new CommandContainer("COMMAND");
        MetaCommand command = new MetaCommand("COMMAND");
        command.setCommandContainer(container);
        command.setAbstract(true);

        ArgumentAssignment assignment = new ArgumentAssignment("type", "0");
        command.addArgumentAssignment(assignment);

        return command;
    }

    private MetaCommand createQUERY() {
        CommandContainer container = new CommandContainer("QUERY");
        MetaCommand command = new MetaCommand("QUERY");
        command.setCommandContainer(container);
        command.setAbstract(true);

        ArgumentAssignment assignment = new ArgumentAssignment("type", "1");
        command.addArgumentAssignment(assignment);

        Argument responseArgument = new Argument("response");
        StringArgumentType responseArgumentType = new StringArgumentType("response");
        StringDataEncoding responseArgumentEncoding = new StringDataEncoding(SizeType.TERMINATION_CHAR);
        responseArgumentEncoding.setTerminationChar((byte) 0x00);
        responseArgumentType.setEncoding(responseArgumentEncoding);
        responseArgument.setArgumentType(responseArgumentType);
        command.addArgument(responseArgument);

        ArgumentEntry responseArgumentEntry = new ArgumentEntry(responseArgument);
        container.addEntry(responseArgumentEntry);

        return command;
    }
}
