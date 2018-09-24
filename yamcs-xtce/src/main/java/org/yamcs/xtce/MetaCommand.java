package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The MetaCommand is the base type for a tele-command.
 * 
 * 
 * <p>
 * The rules for MetaCommand inheritance as follows:
 * <ul>
 * <li>A MetaCommand may extend another using the BaseMetaCommand element</li>
 * <li>BaseMetaCommands that form loops are illegal</li>
 * <li>Its CommandContainer is only inherited if the BaseContainer is explicitly set between the child and parent.</li>
 * <li>The same rules apply to MetaCommand/CommandContainer inheritance as described in
 * SequenceContainer/BaseContainer.</li>
 * </ul>
 * 
 * <p>
 * Specific rules by element and attribute are:
 * <ul>
 * <li>BaseMetaCommand/ArgumentAssignment Child’s content will override parent’s content if present, otherwise child
 * gets parent’s content if it is specified.</li>
 * <li>If argument is the same name, it overrides the parent’s ArgumentAssignment.</li>
 * <li>ArgumentList Child’s content is appended to parent’s content if present</li>
 * <li>CommandContainer Special Case: inherited like other containers if CommandContainer/BaseContainer set. Otherwise
 * it is not inherited.</li>
 *  <li>TransmissionConstraintList Child’s content prefixed to parent’s content if present</li>
 *  <li>DefaultSignificance Child’s content will override parent’s content if present, otherwise child gets parent’s
 * content if specified</li>
 * <li>VerifierSet Child’s content prefixed to parent’s content if present but: - Same verifiers are overridden by the
 * child</li>
 * </ul>
 * @author nm
 */
public class MetaCommand extends NameDescription {
    private static final long serialVersionUID = 5L;

    /**
     * From XTCE:
     * Many commands have one or more options. These are called command arguments. Command arguments may be of any of
     * the standard data types.
     * MetaCommand arguments are local to the MetaCommand. Arguments are the visible to the user or processing software.
     * This can be somewhat subjective -- for example a checksum that is always part of the command format is probably
     * not an argument.
     */
    List<Argument> argumentList = new ArrayList<Argument>();

    /**
     * From XTCE:
     * Tells how to package this command.
     * May not be referred to in the EntryList of a SequenceContainer, CommandContainerSet/CommandContainer or another
     * MetaCommandContainer.
     * May be extended by another MetaCommand/CommandContainer.
     */
    CommandContainer commandContainer;

    MetaCommand baseMetaCommand;
    // assignment for inheritance
    List<ArgumentAssignment> argumentAssignmentList;

    /**
     * From XTCE:
     * Some Command and Control Systems may require special user access or confirmations before transmitting commands
     * with certain levels.
     * The level is inherited from the Base MetaCommand, or it overrides any in the parent-chain if given here, however
     * it should not go down in consequenceLevel.
     */
    private Significance defaultSignificance = null;

    /**
     * if command is abstract, it cannot be instantiated
     */
    boolean abstractCmd = false;

    List<TransmissionConstraint> transmissionContstraintList = new ArrayList<TransmissionConstraint>();

    /**
     * From XTCE
     * A Command Verifier is a conditional check on the telemetry from a SpaceSystem that that provides positive
     * indication on the processing state of a command.
     * There are eight different verifiers each associated with difference states in command processing:
     * TransferredToRange, TransferredFromRange, Received, Accepted, Queued, Execution, Complete, and Failed.
     * There may be multiple ‘complete’ verifiers. ‘Complete’ verifiers are added to the Base MetaCommand ‘Complete’
     * verifier list.
     * All others will override a verifier defined in a Base MetaCommand
     * 
     * 
     * In Yamcs the verifier type is specified in the stage field.
     */
    private List<CommandVerifier> verifierList = new ArrayList<CommandVerifier>();

    public MetaCommand(String name) {
        super(name);
    }

    /**
     * Set the command as abstract or non abstract.
     * Abstract commands cannot be instantiated
     * 
     * @param a
     */
    public void setAbstract(boolean a) {
        abstractCmd = a;
    }

    public boolean isAbstract() {
        return abstractCmd;
    }

    public void setCommandContainer(CommandContainer mcc) {
        this.commandContainer = mcc;
    }

    public CommandContainer getCommandContainer() {
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
     * - this is the list of arguments of the parent(s) which are assigned when the inheritance takes place
     * 
     * returns null if there is no such argument
     * 
     * @return
     */
    public List<ArgumentAssignment> getArgumentAssignmentList() {
        if (argumentAssignmentList == null)
            return null;
        return Collections.unmodifiableList(argumentAssignmentList);
    }

    /**
     * returns the list of arguments of this command
     * can be empty if the command doesn't have arguments
     * 
     * @return
     */
    public List<Argument> getArgumentList() {
        if (argumentList == null)
            return null;
        return Collections.unmodifiableList(argumentList);
    }

    /**
     * returns an argument based on name or null if it doesn't exist
     * 
     * @param argumentName
     * @return
     */
    public Argument getArgument(String argumentName) {
        for (Argument a : argumentList) {
            if (a.getName().equals(argumentName)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Adds an argument to the command.
     * 
     * @param arg
     *            - the argument to be added
     */
    public void addArgument(Argument arg) {
        argumentList.stream().filter(a -> a.getName().equals(arg.getName())).findFirst().ifPresent(a -> {
            throw new IllegalArgumentException("An argument named '" + arg.getName() + "' already exists");
        });
        argumentList.add(arg);
    }

    public void addTransmissionConstrain(TransmissionConstraint constraint) {
        transmissionContstraintList.add(constraint);
    }

    public List<TransmissionConstraint> getTransmissionConstraintList() {
        return transmissionContstraintList;
    }

    public void addArgumentAssignment(ArgumentAssignment aa) {
        if (argumentAssignmentList == null) {
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
        return (!verifierList.isEmpty()) || ((baseMetaCommand != null) && baseMetaCommand.hasCommandVerifiers());
    }

    public List<CommandVerifier> getCommandVerifiers() {
        return Collections.unmodifiableList(verifierList);
    }

    public void print(PrintStream out) {
        out.print("MetaCommand name: " + name + " abstract:" + abstractCmd);
        if (getAliasSet() != null)
            out.print(", aliases: " + getAliasSet());

        if (!transmissionContstraintList.isEmpty()) {
            out.print(", TransmissionConstraints: ");
            out.print(transmissionContstraintList.toString());
        }
        if (defaultSignificance != null) {
            out.print(", defaultSignificance: ");
            out.print(defaultSignificance.toString());
        }

        if (!verifierList.isEmpty()) {
            out.print(", Verifiers: ");
            out.print(verifierList.toString());
        }
        out.println();
        if (baseMetaCommand != null) {
            out.println("\t baseMetaCommand: " + baseMetaCommand.getName() + " with argument assignment:"
                    + argumentAssignmentList);
        }

        commandContainer.print(out);
    }

    public void setArgumentAssignmentList(List<ArgumentAssignment> argumentAssignmentList) {
        this.argumentAssignmentList = argumentAssignmentList;
    }
}
