package org.yamcs.xtceproc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ErrorInCommand;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.MetaCommandContainer;

public class MetaCommandProcessor {
    final ProcessorData pdata;
    final static int MAX_CMD_SIZE = 4096;//should make this configurable
    
    public MetaCommandProcessor(ProcessorData pdata) {
        this.pdata = pdata;
    }
    
    public CommandBuildResult buildCommand(MetaCommand mc, List<ArgumentAssignment> argAssignmentList) throws ErrorInCommand {
        return buildCommand(pdata, mc, argAssignmentList);
    }
    
    public static CommandBuildResult buildCommand(ProcessorData pdata, MetaCommand mc, List<ArgumentAssignment> argAssignmentList) throws ErrorInCommand {
        if(mc.isAbstract()) {
            throw new ErrorInCommand("Will not build command "+mc.getQualifiedName()+" because it is abstract");
        }

        MetaCommandContainer def = mc.getCommandContainer();
        if(def==null) {
            throw new ErrorInCommand("MetaCommand has no container: "+def);
        }
        Map<Argument, Value> args = new HashMap<>();
        Map<String,String> argAssignment = new HashMap<> ();
        for(ArgumentAssignment aa: argAssignmentList) {
            argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
        }

        collectAndCheckArguments(mc, args, argAssignment);
        BitBuffer bitbuf = new BitBuffer(new byte[MAX_CMD_SIZE]);
        TcProcessingContext pcontext = new TcProcessingContext(pdata, bitbuf, 0);
        pcontext.argValues = args;
        pcontext.mccProcessor.encode(mc);

        int length = (bitbuf.getPosition()+7)/8;
        byte[] b = new byte[length];
        System.arraycopy(bitbuf.array(), 0, b, 0, length);
        return new CommandBuildResult(b, args);
    }


    /**
     * Builds the argument values args based on the argAssignment (which is basically the user input) 
     * and on the inheritance assignments
     * 
     * The argAssignment is emptied as values are being used so if at the end of the call there are still assignment not used -> invalid argument provided
     * 
     * This function is called recursively.
     * 
     * @param args
     * @param argAssignment
     * @throws ErrorInCommand 
     */
    private static void collectAndCheckArguments(MetaCommand mc, Map<Argument, Value> args, Map<String, String> argAssignment) throws ErrorInCommand {
        List<Argument> argList = mc.getArgumentList();
        if(argList!=null) {
            //check for each argument that we either have an assignment or a value 
            for(Argument a: argList) {
                if(args.containsKey(a)) {
                    continue;
                }
                String stringValue = null;
                String argInitialValue = null;
                Value argTypeInitialValue = null;
                if(!argAssignment.containsKey(a.getName())) {
                    argInitialValue = a.getInitialValue();
                    argTypeInitialValue = ArgumentTypeProcessor.getInitialValue(a.getArgumentType());
                    if(argInitialValue == null && argTypeInitialValue == null) {
                        throw new ErrorInCommand("No value provided for argument "+a.getName()+" (and the argument has no default value either)");
                    }
                } else {
                    stringValue = argAssignment.remove(a.getName());
                }
                ArgumentType type = a.getArgumentType();
                try {
                    Value v;
                    // default value argInitialValue overwrites argTypeInitialValue
                    if(stringValue == null)
                        stringValue = argInitialValue;
                    if(stringValue !=null)
                        v = ArgumentTypeProcessor.parseAndCheckRange(type, stringValue);
                    else
                        v= argTypeInitialValue;
                    args.put(a,  v);
                } catch (Exception e) {
                    throw new ErrorInCommand("Cannot assign value to "+a.getName()+": "+e.getMessage());
                }
            }
        }

        //now, go to the parent
        MetaCommand parent = mc.getBaseMetaCommand();
        if(parent!=null) {
            List<ArgumentAssignment> aaList = mc.getArgumentAssignmentList();
            if(aaList!=null) {
                for(ArgumentAssignment aa:aaList) {
                    if(argAssignment.containsKey(aa.getArgumentName())) {
                        throw new ErrorInCommand("Cannot overwrite the argument "+aa.getArgumentName()+" which is defined in the inheritance assignment list");
                    }
                    argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
                }
            }
            collectAndCheckArguments(parent, args, argAssignment);
        }		
    }

    static public class CommandBuildResult {
        byte[] cmdPacket;
        Map<Argument, Value> args;

        public CommandBuildResult(byte[] b, Map<Argument, Value> args) {
            this.cmdPacket = b;
            this.args = args;
        }

        public byte[] getCmdPacket() {
            return cmdPacket;
        }

        public Map<Argument, Value> getArgs() {
            return args;
        }
    }
}
