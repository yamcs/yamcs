package com.spaceapplications.yamcs.scpi;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class Args {
    @Parameter(names = "--config", required = true, description = "Load YAML config file.")
    public String config;

    @Parameter(names = "--help", help = true, description = "Print this help message.")
    public boolean help;

    public static Args parse(String[] arguments) {
        Args args = new Args();
        JCommander jc = new JCommander(args);
        jc.setProgramName("yamcs-scpi");

        try {
            jc.parse(arguments);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(-1);
        }

        if (args.help) {
            jc.usage();
            System.exit(0);
        }

        return args;
    }
}
