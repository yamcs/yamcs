package org.yamcs.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.IntFunction;

import org.yamcs.Plugin;
import org.yamcs.PluginManager;
import org.yamcs.PluginMetadata;
import org.yamcs.YamcsVersion;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Console;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;

/**
 * This represents a command together with its options and subcommands
 *
 * <pre>
 * yamcs &lt;options&gt; subcmd &lt;options&gt; subcmd &lt;options&gt;...
 * </pre>
 */
public abstract class Command {
    protected static Console console = JCommander.getConsole();

    // called to exit the VM, overwritten in unit tests
    protected static IntFunction<Void> exitFunction = status -> {
        System.exit(status);
        return null;
    };

    protected JCommander jc = new JCommander(this);
    protected Map<String, Command> subCommands = new LinkedHashMap<>();
    protected Command selectedCommand;
    private final String name;
    protected final Command parent;

    @Parameter(names = { "-h", "--help" }, description = "Show usage", help = true)
    private boolean help;

    public Command(String name, Command parent) {
        this.name = name;
        this.parent = parent;
        jc.setProgramName(getFullCommandName());
    }

    protected void addSubCommand(Command cmd) {
        subCommands.put(cmd.name, cmd);
        jc.addCommand(cmd.name, cmd);
    }

    public void parse(String... args) {
        int k = 0;
        try {
            if (subCommands.isEmpty()) {
                jc.parse(args);
            } else {
                while (k < args.length) {
                    if (args[k].startsWith("-")) {
                        k += getArity(args[k]);
                    } else {
                        break;
                    }
                    k++;
                }
                jc.parse(Arrays.copyOf(args, k));
            }
            if (help) {
                console.println(getUsage());
                exit(0);
            }
        } catch (ParameterException e) {
            console.println(e.getMessage());
            console.println(getUsage());
            exit(1);
        }
        if (subCommands.isEmpty()) {
            return;
        }

        // Special case. Global --version flag prints version info and quits
        if (getRootCommand().version) {
            console.println("yamcs " + YamcsVersion.VERSION + ", build " + YamcsVersion.REVISION);
            PluginManager pluginManager;
            try {
                pluginManager = new PluginManager();
                for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
                    PluginMetadata meta = pluginManager.getMetadata(plugin.getClass());
                    console.println(meta.getName() + " " + meta.getVersion());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            exit(0);
        }

        if (k == args.length) {
            console.println(getUsage());
            exit(1);
        }

        String subcmdName = args[k];

        selectedCommand = subCommands.get(subcmdName);

        if (selectedCommand == null) {
            String fullcmd = getFullCommandName();
            StringBuilder sb = new StringBuilder();
            sb.append(fullcmd).append(": '").append(subcmdName)
                    .append("'").append(" is not a valid command name. See '")
                    .append(fullcmd)
                    .append(" -h'");
            console.println(sb.toString());
            exit(1);
        }
        selectedCommand.parse(Arrays.copyOfRange(args, k + 1, args.length));
    }

    OutputFormat getFormat() {
        return getRootCommand().format;
    }

    private YamcsAdminCli getRootCommand() {
        Command command = this;
        while (command.parent != null) {
            command = command.parent;
        }
        return (YamcsAdminCli) command;
    }

    int getArity(String arg) {
        for (ParameterDescription pd : jc.getParameters()) {
            if (Arrays.asList(pd.getParameter().names()).contains(arg)) {
                return getArity(pd);
            }
        }
        throw new ParameterException("Unknown option '" + arg + "'");
    }

    int getArity(ParameterDescription pd) {
        Class<?> fieldType = pd.getParameterized().getType();
        if ((fieldType == boolean.class || fieldType == Boolean.class)) {
            return 0;
        }

        return pd.getParameter().arity() == -1 ? 1 : pd.getParameter().arity();
    }

    String getFullCommandName() {
        List<Command> a = new ArrayList<>();
        Command c = this;
        while (c != null) {
            a.add(c);
            c = c.parent;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = a.size() - 1;; i--) {
            sb.append(a.get(i).getName());
            if (i == 0) {
                break;
            }
            sb.append(" ");
        }
        return sb.toString();
    }

    String printJsonArray(List<? extends Message> messages) {
        StringBuilder buf = new StringBuilder("[");
        Printer printer = JsonFormat.printer();
        try {
            for (int i = 0; i < messages.size(); i++) {
                if (i != 0) {
                    buf.append(", ");
                }
                printer.appendTo(messages.get(i), buf);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return buf.append("]").toString();
    }

    public String getName() {
        return name;
    }

    void execute() throws Exception {
        if (selectedCommand == null) {
            throw new IllegalStateException("Please implement the execute method in " + this);
        } else {
            selectedCommand.execute();
        }
    }

    void validate() throws ParameterException {
        if (selectedCommand != null) {
            selectedCommand.validate();
        }
    }

    public String getUsage() {
        StringBuilder out = new StringBuilder();
        out.append("usage: " + getFullCommandName()).append(" ");
        List<ParameterDescription> sorted = jc.getParameters();
        Collections.sort(sorted, parameterDescriptionComparator);
        if (!sorted.isEmpty()) {
            out.append("[<options>]");
        }

        if (!subCommands.isEmpty()) {
            out.append(" <command> [<command options>]");
        }
        if (jc.getMainParameter() != null) {
            out.append(" ");
            if (jc.getMainParameter().getParameter().required()) {
                out.append(jc.getMainParameterDescription());
            } else {
                out.append("[").append(jc.getMainParameterDescription()).append("]");
            }
        }
        out.append("\n");
        if (!sorted.isEmpty()) {
            int maxLength = 3 + sorted.stream().map(pd -> pd.getNames().length()).max(Integer::max).get();

            out.append("Options:\n");
            for (ParameterDescription pd : sorted) {
                String descr = pd.getDescription();
                String[] descrArray = descr.split("\\n");
                out.append(String.format("    %-" + maxLength + "s    %s\n", pd.getNames(), descrArray[0]));
                for (int i = 1; i < descrArray.length; i++) {
                    String format = "%-" + (maxLength + pd.getNames().length() + 1) + "s%s\n";
                    out.append(String.format(format, "", descrArray[i]));
                }
            }
        }

        if (!subCommands.isEmpty()) {
            out.append("Commands:\n");
            int maxLength = subCommands.values().stream().mapToInt(c -> c.getName().length()).max().getAsInt();
            for (Command c : subCommands.values()) {
                String descr = jc.getCommandDescription(c.getName());
                String[] descrArray = descr.split("\\n");
                out.append(String.format("    %-" + maxLength + "s    %s\n", c.getName(), descrArray[0]));
                for (int i = 1; i < descrArray.length; i++) {
                    String format = "%-" + (maxLength + c.getName().length() + 3) + "s%s\n";
                    out.append(String.format(format, "", descrArray[i]));
                }
            }
        }
        return out.toString();
    }

    protected static void exit(int status) {
        exitFunction.apply(status);
    }

    private Comparator<? super ParameterDescription> parameterDescriptionComparator = (p0, p1) -> {
        return p0.getLongestName().compareTo(p1.getLongestName());
    };
}
