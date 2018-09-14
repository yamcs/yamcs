package org.yamcs.tctm;

import java.util.Map.Entry;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Tse.CommandDeviceRequest;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.TseLoader;

/**
 * Converts the command into protobuf format as required by the TSE Commander.
 */
public class TseCommandPostprocessor implements CommandPostprocessor {

    public TseCommandPostprocessor(String instance) {
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        String command = null;
        String response = null;
        for (Entry<Argument, Value> entry : pc.getArgAssignment().entrySet()) {
            if (TseLoader.ARG_COMMAND.equals(entry.getKey().getName())) {
                command = entry.getValue().getStringValue();
            } else if (TseLoader.ARG_RESPONSE.equals(entry.getKey().getName())) {
                response = entry.getValue().getStringValue();
            }
        }

        CommandDeviceRequest request = CommandDeviceRequest.newBuilder()
                .setMessage(command)
                .build();

        byte[] b = request.toByteArray();
        byte[] prepended = new byte[4 + b.length];
        prepended[0] = (byte) (b.length >>> 24);
        prepended[1] = (byte) (b.length >>> 16);
        prepended[2] = (byte) (b.length >>> 8);
        prepended[3] = (byte) (b.length);
        System.arraycopy(b, 0, prepended, 4, b.length);

        return prepended;
    }
}
