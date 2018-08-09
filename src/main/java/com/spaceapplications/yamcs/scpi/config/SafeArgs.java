package com.spaceapplications.yamcs.scpi.config;

import static pl.touk.throwing.ThrowingFunction.unchecked;

import java.lang.reflect.Field;
import java.util.Optional;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class SafeArgs {

  static class Args {
    @Parameter(names = "--config", description = "Load YAML config file.")
    public String config;

    @Parameter(names = "--help", description = "Print this help message.")
    public Boolean help;
  }

  private Args args;

  private SafeArgs(Args args) {
    this.args = args;
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(String option) {
    Field f = unchecked(args.getClass()::getField).apply(option);
    return Optional.ofNullable((T) unchecked(f::get).apply(args));
  }

  public static SafeArgs parse(String[] arguments) {
    Args args = new Args();
    JCommander jc = new JCommander(args);
    jc.setProgramName("yamcs-scpi");

    try {
      jc.parse(arguments);
    } catch (ParameterException e) {
      System.out.println(e.getMessage());
      System.exit(-1);
    }

    if (args.help != null) {
      jc.usage();
      System.exit(0);
    }
    return new SafeArgs(args);
  }

}