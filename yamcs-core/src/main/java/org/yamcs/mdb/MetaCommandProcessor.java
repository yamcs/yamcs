package org.yamcs.mdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public class MetaCommandProcessor {
    final ProcessorData pdata;

    public MetaCommandProcessor(ProcessorData pdata) {
        this.pdata = pdata;
    }

    public CommandBuildResult buildCommand(MetaCommand mc, Map<String, Object> argAssignmentList)
            throws ErrorInCommand {
        return buildCommand(pdata, mc, argAssignmentList);
    }

    public static CommandBuildResult buildCommand(ProcessorData pdata, MetaCommand mc,
            Map<String, Object> argAssignmentList) throws ErrorInCommand {
        if (mc.isAbstract()) {
            throw new ErrorInCommand("Not building command " + mc.getQualifiedName() + " because it is abstract");
        }

        ProcessorConfig procConf = pdata.getProcessorConfig();

        Map<Parameter, Value> params = new HashMap<>();

        CommandContainer cmdContainer = mc.getCommandContainer();
        if (cmdContainer == null && !procConf.allowContainerlessCommands()) {
            throw new ErrorInCommand("MetaCommand " + mc.getName()
                    + " has no container (and the processor option allowContainerlessCommands is set to false)");
        }

        if (cmdContainer != null) {
            collectParameters(cmdContainer, params);
        }
        BitBuffer bitbuf = new BitBuffer(new byte[procConf.getMaxCommandSize()]);
        TcProcessingContext pcontext = new TcProcessingContext(mc, pdata, params, bitbuf, 0);

        Map<String, Object> argAssignment = new HashMap<>(argAssignmentList);

        List<ArgumentAssignment> inheritedAssignment = mc.getEffectiveArgumentAssignmentList();

        for (ArgumentAssignment aa : inheritedAssignment) {
            if (argAssignment.containsKey(aa.getArgumentName())) {
                throw new ErrorInCommand("Cannot overwrite the argument " + aa.getArgumentName()
                        + " which is defined in the inheritance assignment list");
            }
            argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
        }
        collectAndCheckArguments(pcontext, argAssignment);

        byte[] binary = null;

        if (cmdContainer != null) {
            try {
                pcontext.mccProcessor.encode(mc);
            } catch (CommandEncodingException e) {
                throw new ErrorInCommand("Error when encoding command: " + e.getMessage());
            }

            int length = pcontext.getSize();
            binary = new byte[length];
            System.arraycopy(bitbuf.array(), 0, binary, 0, length);
        }
        return new CommandBuildResult(binary, pcontext.getArgValues());
    }

    /**
     * Builds the argument values based on the user-provided arguments, initial values, and container-inherited
     * assignments.
     * 
     * The args are emptied as values are being used. If at the end of the call there are unused assignment, then one or
     * more invalid arguments were provided.
     * 
     * This function is called recursively.
     */
    private static void collectAndCheckArguments(TcProcessingContext pcontext, Map<String, Object> args)
            throws ErrorInCommand {

        List<Argument> argList = pcontext.getCommand().getEffectiveArgumentList();
        List<Argument> unassigned = new ArrayList<>();

        if (argList != null) {
            // check for each argument that we either have an assignment or an value
            for (Argument a : argList) {
                if (pcontext.hasArgumentValue(a)) {
                    continue;
                }
                Value argValue = null;
                Object argObj = null;
                if (!args.containsKey(a.getName())) {
                    argObj = a.getInitialValue();
                    if (argObj == null) {
                        argObj = a.getArgumentType().getInitialValue();
                    }

                    if (argObj == null) {
                        unassigned.add(a);
                        continue;
                    }
                } else {
                    Object value = args.remove(a.getName());
                    try {
                        argObj = a.getArgumentType().convertType(value);
                    } catch (Exception e) {
                        throw new ErrorInCommand("Cannot assign value to " + a.getName() + ": " + e.getMessage());
                    }
                }
                try {
                    pcontext.argumentTypeProcessor.checkRange(a.getArgumentType(), argObj);
                    argValue = DataTypeProcessor.getValueForType(a.getArgumentType(), argObj);
                } catch (Exception e) {
                    throw new ErrorInCommand("Cannot assign value to " + a.getName() + ": " + e.getMessage());
                }
                pcontext.addArgumentValue(a, argValue);
            }
        }
        if (!unassigned.isEmpty()) {
            // some arguments may have been assigned by the checkRange method
            // for example arguments used as dynamic array sizes
            unassigned.removeAll(pcontext.getCmdArgs().keySet());
            if (!unassigned.isEmpty()) {
                throw new ErrorInCommand("No value provided for arguments: "
                        + unassigned.stream().map(arg -> arg.getName()).collect(Collectors.joining(", ", "[", "]")));
            }
        }
    }

    // look at the command container if it inherits another container using a condition list and add those parameters
    // with the respective values
    private static void collectParameters(Container container, Map<Parameter, Value> params) throws ErrorInCommand {
        Container parent = container.getBaseContainer();
        if (parent != null) {
            MatchCriteria cr = container.getRestrictionCriteria();
            if (cr instanceof ComparisonList) {
                ComparisonList cl = (ComparisonList) cr;
                for (Comparison c : cl.getComparisonList()) {
                    if (c.getComparisonOperator() == OperatorType.EQUALITY) {
                        Parameter param = ((ParameterInstanceRef) c.getRef()).getParameter();
                        if (param != null) {
                            try {
                                Value v = ParameterTypeUtils.parseString(param.getParameterType(), c.getStringValue());
                                params.put(param, v);
                            } catch (IllegalArgumentException e) {
                                throw new ErrorInCommand("Cannot parse '" + c.getStringValue()
                                        + "' as value for parameter " + param.getQualifiedName());
                            }
                        }
                    }
                }
            }
        }
    }

    static public class CommandBuildResult {
        byte[] cmdPacket;
        Map<Argument, ArgumentValue> args;

        public CommandBuildResult(byte[] b, Map<Argument, ArgumentValue> args) {
            this.cmdPacket = b;
            this.args = args;
        }

        public byte[] getCmdPacket() {
            return cmdPacket;
        }

        public Map<Argument, ArgumentValue> getArgs() {
            return args;
        }
    }
}
