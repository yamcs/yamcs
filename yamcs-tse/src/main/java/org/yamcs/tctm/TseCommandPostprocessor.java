package org.yamcs.tctm;

import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Tse.TseCommand;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.TseLoader;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Converts the command into protobuf format as required by the TSE Commander.
 */
public class TseCommandPostprocessor implements CommandPostprocessor {

    // Parameter references are surrounded by backticks (to distinguish from
    // the angle brackets which are used for argument substitution)
    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("`(.*?)`");

    private XtceDb xtcedb;

    public TseCommandPostprocessor(String yamcsInstance) {
        xtcedb = XtceDbFactory.getInstance(yamcsInstance);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        MetaCommand mc = pc.getMetaCommand();
        String subsystemName = mc.getSubsystemName();
        SpaceSystem subsystem = xtcedb.getSpaceSystem(subsystemName);

        TseCommand.Builder msgb = TseCommand.newBuilder()
                .setInstrument(subsystem.getName());

        for (Entry<Argument, Value> entry : pc.getArgAssignment().entrySet()) {
            String name = entry.getKey().getName();
            Value v = entry.getValue();
            switch (name) {
            case TseLoader.ARG_COMMAND:
                msgb.setCommand(v.getStringValue());
                break;
            case TseLoader.ARG_RESPONSE:
                msgb.setResponse(v.getStringValue());
                break;
            default:
                msgb.putArgumentMapping(name, ValueUtility.toGbp(v));
            }
        }

        if (msgb.hasResponse()) {
            Matcher m = PARAMETER_REFERENCE.matcher(msgb.getResponse());
            while (m.find()) {
                String name = m.group(1);
                String qname = subsystem.getQualifiedName() + "/" + name;
                msgb.putParameterMapping(name, qname);
            }
        }

        TseCommand command = msgb.build();

        byte[] b = command.toByteArray();
        byte[] prepended = new byte[4 + b.length];
        prepended[0] = (byte) (b.length >>> 24);
        prepended[1] = (byte) (b.length >>> 16);
        prepended[2] = (byte) (b.length >>> 8);
        prepended[3] = (byte) (b.length);
        System.arraycopy(b, 0, prepended, 4, b.length);

        return prepended;
    }
}
