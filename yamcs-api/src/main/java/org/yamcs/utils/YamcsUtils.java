package org.yamcs.utils;

import java.util.List;

import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;

/**
 * Collection of utility methods 
 *
 */
public class YamcsUtils {

    public static List<YamcsInstance> getInstanceList(String url) throws Exception {
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        try {
            ys = YamcsSession.newBuilder().setConnectionParams(url).build();
            msgClient = ys.newClientBuilder().setRpc(true).build();
            YamcsInstances ainst = (YamcsInstances)msgClient.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getYamcsInstances", null, YamcsInstances.newBuilder());
            return ainst.getInstanceList();
        } finally {
            if (msgClient != null) {
                msgClient.close();
            }
            if (ys != null) {
                ys.close();
            }
        }
    }
	/**
	 * Get list of yamcs instances
	 * @param host Hostname of IP address
	 * @param port Port number
	 * @return List of running instances
	 * @throws Exception
	 */
	public static List<YamcsInstance> getInstanceList(String host, int port) throws Exception {
	    return getInstanceList("yamcs://"+host+":"+port);
	}
	
	public static void main(String[] args) throws Exception {
		getInstanceList("aces-eds", 5445);
	}
}
