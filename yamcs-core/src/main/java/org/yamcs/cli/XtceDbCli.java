package org.yamcs.cli;

import java.util.List;
import java.util.Set;

import org.yamcs.YConfiguration;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Provides information about the xtce database")
public class XtceDbCli extends Command {

    public XtceDbCli(Command parent) {
        super("xtcedb", parent);
        addSubCommand(new XtceDbListConfigs());
        addSubCommand(new XtceDbPrint());
        addSubCommand(new XtceDbVerify());
    }

    @Parameters(commandDescription = "Print the contents of the XtceDB.")
    private class XtceDbPrint extends Command {
        @Parameter(required=true, description ="config-name")
     //   @Parameter(names="--config", description="XtceDB config name from mdb.yaml", required=true)
        private List<String> args;

        public XtceDbPrint() {
            super("print", XtceDbCli.this);
        }

        @Override
        void validate() {
            if(args.size()> 1) {
                throw new ParameterException("Please specify only one configuration.");
            }
        }
        @Override
        void execute() throws Exception {
            YConfiguration.setup();
            XtceDb xtcedb = XtceDbFactory.createInstanceByConfig(args.get(0));
            xtcedb.print(System.out);
        }
    }
    
    
    @Parameters(commandDescription = "Verify  that the XtceDB can be loaded.")
    private class XtceDbVerify extends Command {
        @Parameter(required=true, description ="config-name")
     //   @Parameter(names="--config", description="XtceDB config name from mdb.yaml", required=true)
        private List<String> args;

        public XtceDbVerify() {
            super("verify", XtceDbCli.this);
        }

        @Override
        void validate() {
            if(args.size()> 1) {
                throw new ParameterException("Please specify only one configuration.");
            }
        }
        @Override
        void execute() throws Exception {
            YConfiguration.setup();
            XtceDb xtcedb = XtceDbFactory.createInstanceByConfig(args.get(0), false);
            console.println("The XtceDB was loaded without error; it contains ");
            console.println(String.format("%10d subsystems", xtcedb.getSpaceSystems().size()));
            console.println(String.format("%10d parameters", xtcedb.getParameters().size()));
            console.println(String.format("%10d sequence containers", xtcedb.getSequenceContainers().size()));
            console.println(String.format("%10d commands", xtcedb.getMetaCommands().size()));
        }
    }
    
    
    @Parameters(commandDescription = "List the MDB configurations defined in mdb.yaml.")
    private class XtceDbListConfigs extends Command {

        public XtceDbListConfigs() {
            super("listConfigs", XtceDbCli.this);
        }

        @Override
        void execute() throws Exception {
            YConfiguration.setup();
            YConfiguration c = YConfiguration.getConfiguration("mdb");
            Set<String> keys = c.getKeys();
            for(String s: keys) {
               console.println(s);
            }
        }
    }
}
