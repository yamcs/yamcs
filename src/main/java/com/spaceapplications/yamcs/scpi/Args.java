package com.spaceapplications.yamcs.scpi;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Args {
  @Parameter(names = "--config", description = "Load YAML config file.")
  public String config;

  @Parameter(names = "--help", description = "Print this help message.")
  public boolean help;

  public static Args parse(String[] arguments) {
    Args args = new Args();
    JCommander jc = new JCommander(args);
    jc.setProgramName("yamcs-scpi");
    jc.parse(arguments);

    if (args.help) {
      jc.usage();
      System.exit(0);
    }
    return args;
  }
}