package org.yamcs.cli;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.yamcs.YConfiguration;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Provides MDB information")
public class MdbCli extends Command {

    public MdbCli(Command parent) {
        super("mdb", parent);
        addSubCommand(new XtceDbPrint());
        addSubCommand(new XtceDbVerify());
    }

    private static XtceDb getMdb(String specOrInstance) {
        Set<String> mdbSpecs = Collections.emptySet();
        if (YConfiguration.isDefined("mdb")) {
            mdbSpecs = YConfiguration.getConfiguration("mdb").getKeys();
        }

        if (mdbSpecs.contains(specOrInstance)) {
            return XtceDbFactory.createInstanceByConfig(specOrInstance);
        } else {
            return XtceDbFactory.getInstance(specOrInstance);
        }
    }

    @Parameters(commandDescription = "Print MDB content")
    private class XtceDbPrint extends Command {

        @Parameter(required = true, description = "INSTANCE")
        private List<String> args;

        public XtceDbPrint() {
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
            XtceDb xtcedb = getMdb(args.get(0));
            xtcedb.print(System.out);
        }
    }

    @Parameters(commandDescription = "Verify that the MDB can be loaded")
    private class XtceDbVerify extends Command {

        @Parameter(required = true, description = "INSTANCE")
        private List<String> args;

        public XtceDbVerify() {
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
            XtceDb xtcedb = getMdb(args.get(0));
            console.println("MDB loaded successfully. Contents:");
            console.println(String.format("%10d subsystems", xtcedb.getSpaceSystems().size()));
            console.println(String.format("%10d parameters", xtcedb.getParameters().size()));
            console.println(String.format("%10d sequence containers", xtcedb.getSequenceContainers().size()));
            console.println(String.format("%10d commands", xtcedb.getMetaCommands().size()));
        }
    }
}
