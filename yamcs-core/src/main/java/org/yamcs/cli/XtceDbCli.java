package org.yamcs.cli;

import java.util.Set;

import org.yamcs.YConfiguration;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Provides information about the xtce database")
public class XtceDbCli extends Command {

    public XtceDbCli(Command parent) {
        super("xtcedb", parent);
        addSubCommand(new XtceDbListConfigs());
        addSubCommand(new XtceDbPrint());
    }

    @Parameters(commandDescription = "Print the contents of the XtceDB.")
    private class XtceDbPrint extends Command {
        @Parameter(names="--config", description="XtceDB config name from mdb.yaml", required=true)
        private String config;

        public XtceDbPrint() {
            super("print", XtceDbCli.this);
        }

        @Override
        void execute() throws Exception {
            YConfiguration.setup();
            XtceDb xtcedb = XtceDbFactory.createInstanceByConfig(config);
            xtcedb.print(System.out);
        }
    }
    
    @Parameters(commandDescription = "List the MDB configuration defined in mdb.yaml.")
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
