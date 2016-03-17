package org.yamcs.tctm;

import java.io.IOException;
import org.yamcs.ConfigurationException;
/**
 * @deprecated please use {@link MulticastTmDataLink} instead
 */
@Deprecated
public class MulticastTmProvider extends MulticastTmDataLink {
    public MulticastTmProvider(String instance, String name, String spec) throws ConfigurationException  {
        super(instance, name, spec);
    }

    public MulticastTmProvider(String group, int port) throws IOException {
        super(group, port);
    }
}
