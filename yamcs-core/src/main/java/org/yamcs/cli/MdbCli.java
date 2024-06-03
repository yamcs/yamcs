package org.yamcs.cli;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.yamcs.YConfiguration;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.Mdb;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Provides MDB information")
public class MdbCli extends Command {

    public MdbCli(Command parent) {
        super("mdb", parent);
        addSubCommand(new MdbPrint());
        addSubCommand(new MdbVerify());
    }

    private static Mdb getMdb(String specOrInstance) {
        Set<String> mdbSpecs = Collections.emptySet();
        if (YConfiguration.isDefined("mdb")) {
            mdbSpecs = YConfiguration.getConfiguration("mdb").getKeys();
        }

        if (mdbSpecs.contains(specOrInstance)) {
            return MdbFactory.createInstanceByConfig(specOrInstance);
        } else {
            return MdbFactory.getInstance(specOrInstance);
        }
    }

    @Parameters(commandDescription = "Print MDB content")
    private class MdbPrint extends Command {

        @Parameter(required = true, description = "INSTANCE")
        private List<String> args;

        public MdbPrint() {
            super("print", MdbCli.this);
        }

        @Override
        void validate() {
            if (args.size() > 1) {
                throw new ParameterException("Specify only one configuration");
            }
        }

        @Override
        void execute() throws Exception {
            YConfiguration.setupTool();
            Mdb mdb = getMdb(args.get(0));
            mdb.print(new PrintStream(System.err) {

                @Override
                public void print(String x) {
                    console.print(x);
                }

                @Override
                public void println() {
                    console.println("");
                }

                @Override
                public void println(String x) {
                    console.println(x);
                }
            });
        }
    }

    @Parameters(commandDescription = "Verify that the MDB can be loaded")
    private class MdbVerify extends Command {

        @Parameter(required = true, description = "INSTANCE")
        private List<String> args;

        public MdbVerify() {
            super("verify", MdbCli.this);
        }

        @Override
        void validate() {
            if (args.size() > 1) {
                throw new ParameterException("Specify only one configuration");
            }
        }

        @Override
        void execute() throws Exception {
            YConfiguration.setupTool();
            Mdb mdb = getMdb(args.get(0));
            console.println("MDB loaded successfully. Contents:");
            console.println(String.format("%10d subsystems", mdb.getSpaceSystems().size()));
            console.println(String.format("%10d parameters", mdb.getParameters().size()));
            console.println(String.format("%10d sequence containers", mdb.getSequenceContainers().size()));
            console.println(String.format("%10d commands", mdb.getMetaCommands().size()));
        }
    }
}
