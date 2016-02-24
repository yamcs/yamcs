package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ErrorInCommand;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.MetaCommandContainer;

public class MetaCommandProcessor {
    static public CommandBuildResult buildCommand(MetaCommand mc, List<ArgumentAssignment> argAssignmentList) throws ErrorInCommand {
        if(mc.isAbstract()) {
            throw new ErrorInCommand("Will not build command "+mc.getQualifiedName()+" because it is abstract");
        }

        MetaCommandContainer def=null;
        def=mc.getCommandContainer();
        if(def==null) {
            throw new ErrorInCommand("MetaCommand has no container: "+def);
        }
        Map<Argument, Value> args = new HashMap<Argument,Value>();
        Map<String,String> argAssignment = new HashMap<String, String> ();
        for(ArgumentAssignment aa: argAssignmentList) {
            argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
        }

        collectAndCheckArguments(mc, args, argAssignment);

        TcProcessingContext pcontext = new TcProcessingContext(ByteBuffer.allocate(1000), 0);
        pcontext.argValues = args;
        pcontext.mccProcessor.encode(mc);


        byte[] b = new byte[pcontext.size];
        pcontext.bb.position(0);
        pcontext.bb.get(b, 0, pcontext.size);
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
                if(args.containsKey(a)) continue;
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
