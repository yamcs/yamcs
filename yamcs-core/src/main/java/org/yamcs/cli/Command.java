package org.yamcs.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Console;

/**
 * This represents a command together with its options and subcommands 
 * 
 *     yamcs <options> subcmd <options> subcmd <options>...
 * 
 * 
 */
public abstract class Command {
    static protected Console console = JCommander.getConsole();

    protected JCommander jc = new JCommander(this);
    protected Map<String, Command> subCommands = new HashMap<>();
    protected Command selectedCommand;
    final String name;
    final Command parent;
    Logger log = LoggerFactory.getLogger(this.getClass());

    @Parameter(names="-h", description="help", help = true)
    private boolean help;

    private boolean ycpRequired = false;
    private boolean instanceRequired = false;
    
    public void setYcpRequired(boolean requiredYcp, boolean requireInstance) {
        this.ycpRequired = requiredYcp;
        this.instanceRequired = requireInstance;
    }

    public Command(String name, Command parent) {
        this.name = name;
        this.parent = parent;
        jc.setProgramName(getFullCommandName());
    }

    protected void addSubCommand(Command cmd) {
        subCommands.put(cmd.name, cmd);
        jc.addCommand(cmd.name, cmd);
    }
    protected YamcsConnectionProperties getYamcsConnectionProperties() {
        Command c = this;
        while(c!=null) {
            if(c instanceof YamcsCli) {
                return ((YamcsCli) c).ycp;
            }
            c = c.parent;
        }
        return null;
    }

    public void parse(String[] args) {
        int k = 0;
        try {
            if(subCommands.isEmpty()) {
                jc.parse(args);
            } else { 
                while(k<args.length) {
                    if(args[k].startsWith("-")) {
                        k+=getArity(args[k]);
                    } else {
                        break;
                    }
                    k++;
                }
                jc.parse(Arrays.copyOf(args, k));
            }
            if(help) {
                console.println(getUsage());
                System.exit(0);
            }
        } catch (ParameterException e) {
            console.println(e.getMessage());
            console.println(getUsage());
            System.exit(1);
        }
        if(subCommands.isEmpty()) {
            return;
        }

        if(k==args.length) {
            JCommander.getConsole().println(getUsage());
            System.exit(1);
        }

        String subcmdName = args[k];

        selectedCommand = subCommands.get(subcmdName); 


        if(selectedCommand==null) {
            String fullcmd = getFullCommandName();
            StringBuilder sb = new StringBuilder();
            sb.append(fullcmd).append(": '").append(subcmdName)
            .append("'").append(" is not a valid command name. See '")
            .append(fullcmd)        	
            .append(" -h'");
            JCommander.getConsole().println(sb.toString());
            System.exit(1);
        }
        selectedCommand.parse(Arrays.copyOfRange(args, k+1, args.length));
    }


    int getArity(String arg) {
        for(ParameterDescription pd: jc.getParameters()) {
            if(Arrays.asList(pd.getParameter().names()).contains(arg)) {
                return getArity(pd);
            }
        }
        throw new ParameterException("Unkown option '"+arg+"'");
    }
    
    int getArity(ParameterDescription pd) {
        Class<?> fieldType = pd.getParameterized().getType();
        if ((fieldType == boolean.class || fieldType == Boolean.class)) {
            return 0;
        }
        
        return pd.getParameter().arity()==-1?1:pd.getParameter().arity();
    }

    String getFullCommandName() {
        List<Command> a = new ArrayList<Command>();
        Command c = this;
        while(c!=null) {
            a.add(c);
            c = c.parent;
        }
        StringBuilder sb = new StringBuilder();
        for(int i=a.size()-1;;i--) {
            sb.append(a.get(i).getName());
            if(i==0) {
                break;
            }
            sb.append(" ");
        }
        return sb.toString();
    }

    public String getName() {
        return name;
    }

    void execute() throws Exception {		
        if(selectedCommand==null) {
            throw new IllegalStateException("Please implement the execute method in "+this);
        } else {
            selectedCommand.execute();
        }
    }

    void validate() throws ParameterException {
        if(ycpRequired) {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            if(ycp==null) {
                throw new ParameterException("This command requires a connection to a live yamcs. Please use the 'yamcs -y' option");
            }
            if(instanceRequired && ycp.getInstance()==null) {
                throw new ParameterException("This command requires the yamcs instnace specified in the yamcs url.  Please use the 'yamcs -y http://host:port/instance' option");
            }
        }
        if(selectedCommand!=null) {
            selectedCommand.validate();
        }
    }

    public String getUsage() {
        StringBuilder out = new StringBuilder();
        out.append("usage: "+getFullCommandName()).append(" ");
        List<ParameterDescription> sorted = jc.getParameters();
        Collections.sort(sorted, parameterDescriptionComparator);
        if(!sorted.isEmpty()) {
            out.append("[<options>]");
        }

        if(!subCommands.isEmpty()) {
            out.append(" <command> [<command options>]");
        }
        if(jc.getMainParameter()!=null) {
            out.append(" ");
            out.append(jc.getMainParameterDescription());
        }
        out.append("\n");
        if (sorted.size() > 0) {
            int maxLength = 3+sorted.stream().map(pd -> pd.getNames().length()).max(Integer::max).get();

            out.append("Options:\n");
            for (ParameterDescription pd : sorted) {
                out.append(String.format("    %-"+maxLength+"s    %s\n",pd.getNames(), pd.getDescription()));
            }
        }	
        if(!subCommands.isEmpty()) {
            out.append("Commands:\n");
            int maxLength = 2+subCommands.values().stream().map(c -> c.getName().length()).max(Integer::max).get();
            for(Command c: subCommands.values()) {
                out.append(String.format("    %-"+maxLength+"s    %s\n",c.getName(), jc.getCommandDescription(c.getName())));
            }
        }
        return out.toString();
    }

    private Comparator<? super ParameterDescription> parameterDescriptionComparator
    = new Comparator<ParameterDescription>() {
        @Override
        public int compare(ParameterDescription p0, ParameterDescription p1) {
            return p0.getLongestName().compareTo(p1.getLongestName());
        }
    };
}