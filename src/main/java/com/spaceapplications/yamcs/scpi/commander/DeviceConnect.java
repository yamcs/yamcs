package com.spaceapplications.yamcs.scpi.commander;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeviceConnect extends Command {
  private List<Device> devices = new ArrayList<>();

  public interface Device {
    public String id();

    public void open();

    public void close();

    public String exec(String cmd);
  }

  public DeviceConnect(String cmd, String description, HasContext context, List<Device> devices) {
    super(cmd, description, context);
    this.devices = devices;
  }

  @Override
  String handleExecute(String deviceId) {
    Optional<Device> device = findDevice(deviceId);
    if (!device.isPresent())
      return deviceId + ": device not found";
    device.get().open();
    String prompt = "device:" + deviceId + Command.DEFAULT_PROMPT;
    setPrompt(prompt);
    Command parent = this;
    Command contextCmd = new Command("", "", context) {
      @Override
      String handleExecute(String cmd) {
        if (Commander.isCtrlD(cmd))
          return disconnect(deviceId, parent, device.get());
        return device.get().exec(cmd);
      }
    };
    contextCmd.setPrompt(prompt);
    context.setContextCmd(contextCmd);
    return "connect to: " + deviceId;
  }

  private Optional<Device> findDevice(String deviceId) {
    return devices.stream().filter(d -> deviceId.equals(d.id())).findFirst();
  }

  private String disconnect(String deviceId, Command parent, Device device) {
    setPrompt(Command.DEFAULT_PROMPT);
    parent.setPrompt(Command.DEFAULT_PROMPT);
    context.clearContextCmd();
    device.close();
    return "\ndisconnect from " + deviceId;
  }
}