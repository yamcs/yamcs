package org.yamcs.tse.commander;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of reusable device connections, closing them when they are idle for too long.
 */
public class DevicePool {

    private List<Device> devices = new ArrayList<>();

    public void add(Device device) {
        devices.add(device);
    }

    public Device getDevice(String id) {
        for (Device device : devices) {
            if (id.equals(device.getId())) {
                return device;
            }
        }
        return null;
    }

    public List<Device> getDevices() {
        return devices;
    }
}
