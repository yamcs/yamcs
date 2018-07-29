package org.yamcs.server.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Provides information about the xtce database")
public class XtceDbCli extends Command {
    @Parameter(names="-f", required=false, description ="Use this file instead of default mdb.yaml")
    String mdbConfigFile;

    public XtceDbCli(Command parent) {
        super("xtcedb", parent);
        addSubCommand(new XtceDbListConfigs());
        addSubCommand(new XtceDbPrint());
        addSubCommand(new XtceDbVerify());
    }

    private XtceDb getXtceDb(String configSection) {
        XtceDb xtcedb;
        if(mdbConfigFile==null) {
            xtcedb = XtceDbFactory.createInstanceByConfig(configSection);
        } else {
            Yaml yaml=new Yaml();
            try (InputStream is = new FileInputStream(mdbConfigFile)){
                Object o = yaml.load(is);
                if(o==null) {
                    throw new ConfigurationException(mdbConfigFile, mdbConfigFile+": file is empty!?");
                } else if(!(o instanceof Map<?, ?>)) {
                    throw new ConfigurationException(mdbConfigFile, mdbConfigFile+": top level structure must be a map and not a "+o);
                }
                o = ((Map<String, Object>)o).get(configSection);
                if(o==null) {
                    throw new ConfigurationException(mdbConfigFile, mdbConfigFile+": does not contain a mapping for "+configSection);
                } else if(!(o instanceof List<?>)) {
                    throw new ConfigurationException(mdbConfigFile, mdbConfigFile+": mapping for "+configSection+" must be a list and not a "+o.getClass());
                }
                List<Object> list = (List<Object>) o;
                xtcedb = XtceDbFactory.createInstance(list, false, false);
                
            } catch (YAMLException|IOException e) {
                throw new ConfigurationException(mdbConfigFile, e.toString(), e);
            }
        }
        return xtcedb;
    }

    @Parameters(commandDescription = "Print the contents of the XtceDB.")
    private class XtceDbPrint extends Command {
        @Parameter(required=true, description ="config-name")
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
            XtceDb xtcedb = getXtceDb(args.get(0));
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
            if(mdbConfigFile==null) {
                YConfiguration.setup();
            }
            XtceDb xtcedb = getXtceDb(args.get(0));
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
            Set<String> keys;
            if(mdbConfigFile==null) {
                YConfiguration.setup();
                YConfiguration c = YConfiguration.getConfiguration("mdb");
                keys = c.getKeys();
            } else {
                Yaml yaml=new Yaml();
                try (InputStream is = new FileInputStream(mdbConfigFile)){
                    Object o = yaml.load(is);
                    if(o==null) {
                        throw new ConfigurationException(mdbConfigFile, "file is empty!?");
                    } else if(!(o instanceof Map<?, ?>)) {
                        throw new ConfigurationException(mdbConfigFile, "top level structure must be a map and not a "+o);
                    }
                    keys = ((Map<String, Object>)o).keySet();
                }
            }
            for(String s: keys) {
                console.println(s);
            }
        }
    }
}
