package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A type definition used as the base type for a CommandDefinition
 * 
 * 
 * @author nm
 *
 */
public class MetaCommand extends NameDescription {
    private static final long serialVersionUID = 5L;

    /**
     * From XTCE:
     * Many commands have one or more options.  These are called command arguments.  Command arguments may be of any of the standard data types.  
     * MetaCommand arguments are local to the MetaCommand. Arguments are the visible to the user or processing software. 
     * This can be somewhat subjective -- for example a checksum that is always part of the command format is probably not an argument.
     */
    List<Argument> argumentList = new ArrayList<Argument>();


    /**
     * From XTCE:
     * Tells how to package this command. 
     * May not be referred to in the EntryList of a SequenceContainer, CommandContainerSet/CommandContainer or another MetaCommandContainer. 
     * May be extended by another MetaCommand/CommandContainer.
     */
    MetaCommandContainer commandContainer;


    /** Command inheritance
     * 
     * From XTCE:
     *  The rules for MetaCommand inheritance as follows:  
     *   A MetaCommand may extend another using the BaseMetaCommand element --- BaseMetaCommands that form loops are illegal 
     *   --- Its CommandContainer is only inherited if the BaseContainer is explicitly set between the child and parent.  
     *   The same rules apply to MetaCommand/CommandContainer inheritance as described in SequenceContainer/BaseContainer.  
     *   Specific rules by element and attribute are:
     *       BaseMetaCommand/ArgumentAssignment  Child’s content will override parent’s content if present, otherwise child gets parent’s content if it is specified.  
     *       If argument is the same name, it overrides the parent’s ArgumentAssignment. --- 
     *      
     *       ArgumentList Child’s content prefixed to parent’s content if present -- 
     *       CommandContainer  Special Case:  inherited like other containers if CommandContainer/BaseContainer set.  Otherwise it is not inherited. --- 
     *       
     *       
     *       Below, nothing is implemented TODO:
     *       AncillaryDataSet  Child’s content prefixed to parent’s content if present ---
     *       TransmissionConstraintList  Child’s content prefixed to parent’s content if present -- 
     *       DefaultSignificance  Child’s content will override parent’s content if present, otherwise child gets parent’s content if specified --- 
     *       ContextSignificanceList  Child’s content prefixed to parent’s content if present --- 
     *       Interlock  Child’s content will override parent’s content if present, otherwise child gets parent’s content if specified --- 
     *       VerifierSet  Child’s content prefixed to parent’s content if present but: -  Same verifiers are overridden by the child -  
     *       CommandCompletes are accrued (child elements prefixed to parent’s). -  If the child’s CommandComplete has the same @name as parent’s, the child overrides it --- 
     *       ParameterToSetList  Child’s content prefixed to parent’s content if present. If the @parameterRef is the same, the child overrides the parent’s --- 
     *       ParameterToSuspendAlarmsOnSet  Child’s content prefixed to parent’s content if present. If the @parameterRef is the same, the child overrides the parent’s.
     *       
     *       DIFFERS_FROM_XTCE: the prefixing of base attributes by child does not make sense to me. We instead implement appending - that is the argument of the parent come first, then the child.
     *       Note that if argument entries are specified by absolute positions, it doesn't matter whichever comes first except maybe the order in the user interface.
     */
    MetaCommand baseMetaCommand;
    //assignment for inheritance
    List<ArgumentAssignment> argumentAssignmentList;

    
    /**
     * From XTCE:
     * Some Command and Control Systems may require special user access or confirmations before transmitting commands with certain levels.  
     * The level is inherited from the Base MetaCommand, or it overrides any in the parent-chain if given here, however it should not go down in consequenceLevel.
     */
    private Significance defaultSignificance = null;
    
    /**
     * if command is abstract, it cannot be instantiated
     */
    boolean abstractCmd = false;

    List<TransmissionConstraint> transmissionContstraintList = new ArrayList<TransmissionConstraint>();
    
    /**
     * From XTCE
     * A Command Verifier is a conditional check on the telemetry from a SpaceSystem that that provides positive indication on the processing state of a command. 
     * There are eight different verifiers each associated with difference states in command processing:
     * TransferredToRange, TransferredFromRange, Received, Accepted, Queued, Execution, Complete, and Failed. 
     *  There may be multiple ‘complete’ verifiers. ‘Complete’ verifiers are added to the Base MetaCommand ‘Complete’ verifier list. 
     *  All others will override a verifier defined in a Base MetaCommand
     *  
     *  
     *  In Yamcs the verifier type is specified in the stage field.
    */
    private List<CommandVerifier> verifierList = new ArrayList<CommandVerifier>();
    
    
    
    public MetaCommand(String name) {
	super(name);
    }

    /**
     * Set the command as abstract or non abstract.
     * Abstract commands cannot be instantiated
     * @param a
     */
    public void setAbstract(boolean a) {
	abstractCmd = a;
    }

    public boolean isAbstract() {
	return abstractCmd;
    }


    public void setMetaCommandContainer(MetaCommandContainer mcc) {
	this.commandContainer = mcc;
    }

    public MetaCommandContainer getCommandContainer() {
	return commandContainer;
    }

    public void setBaseMetaCommand(MetaCommand mc) {
	this.baseMetaCommand = mc;
    }

    public MetaCommand getBaseMetaCommand() {
	return baseMetaCommand;
    }
    /**
     * returns the argument assignment list in relation to the inheritance 
     *   - this is the list of arguments of the parent(s) which are assigned when the inheritance takes place
     * 
     * returns null if there is no such argument
     * @return
     */
    public List<ArgumentAssignment> getArgumentAssignmentList() {
	if(argumentAssignmentList==null) return null;
	return Collections.unmodifiableList(argumentAssignmentList);
    }

    /**
     * returns the list of arguments of this command
     * can be empty if the command doesn't have arguments
     * @return
     */
    public List<Argument> getArgumentList() {
	if(argumentList==null) return null;
	return Collections.unmodifiableList(argumentList);
    }
    /**
     * returns an argument based on name or null if it doesn't exist
     * @param argumentName
     * @return
     */
    public Argument getArgument(String argumentName) {
	for(Argument a: argumentList) {
	    if(a.getName().equals(argumentName)) {
		return a;
	    }
	}
	return null;
    }

    public void addArgument(Argument arg) {
	argumentList.add(arg);
    }

    public void addTransmissionConstrain(TransmissionConstraint constraint) {
	transmissionContstraintList.add(constraint);
    }
    
    public List<TransmissionConstraint> getTransmissionConstraintList() {
	return transmissionContstraintList;
    }
    
    
    public void addArgumentAssignment(ArgumentAssignment aa) {
	if(argumentAssignmentList==null) {
	    argumentAssignmentList = new ArrayList<ArgumentAssignment>();
	}
        argumentAssignmentList.add(aa);
    }

    public boolean hasTransmissionConstraints() {
	return !transmissionContstraintList.isEmpty();
    }

    public Significance getDefaultSignificance() {
        return defaultSignificance;
    }

    public void setDefaultSignificance(Significance defaultSignificance) {
        this.defaultSignificance = defaultSignificance;
    }
    
    public void addVerifier(CommandVerifier cmdVerifier) {
        verifierList.add(cmdVerifier);
    }


    public boolean hasCommandVerifiers() {        
        return (!verifierList.isEmpty()) || ((baseMetaCommand!=null) && baseMetaCommand.hasCommandVerifiers());
    }
    
    public List<CommandVerifier> getCommandVerifiers() {
        return Collections.unmodifiableList(verifierList);
    }
    
    public void print(PrintStream out) {
        out.print("MetaCommand name: "+name+" abstract:"+abstractCmd);
        if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
                
        if(!transmissionContstraintList.isEmpty()) {
            out.print(", TransmissionConstraints: ");
            out.print(transmissionContstraintList.toString());
        }
        if(defaultSignificance!=null) {
            out.print(", defaultSignificance: ");
            out.print(defaultSignificance.toString());
        }
        
        if(!verifierList.isEmpty()) {
            out.print(", Verifiers: ");
            out.print(verifierList.toString());
        }
        out.println();
        if(baseMetaCommand!=null) {
            out.println("\t baseMetaCommand: "+baseMetaCommand.getName()+" with argument assignment:" +argumentAssignmentList);
        }
        
        
        commandContainer.print(out);
    }
}