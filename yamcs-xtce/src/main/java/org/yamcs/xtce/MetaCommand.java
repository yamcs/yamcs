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
 * <li>TransmissionConstraintList Child’s content prefixed to parent’s content if present</li>
 * <li>DefaultSignificance Child’s content will override parent’s content if present, otherwise child gets parent’s
 * content if specified</li>
 * <li>VerifierSet Child’s content prefixed to parent’s content if present but: - Same verifiers are overridden by the
 * child</li>
 * </ul>
 * 
 * @author nm
 */
public class MetaCommand extends NameDescription {
    private static final long serialVersionUID = 6L;

    /**
     * From XTCE: Many commands have one or more options. These are called command arguments. Command arguments may be
     * of any of the standard data types.
     * <p>
     * MetaCommand arguments are local to the MetaCommand. Arguments are the visible to the user or processing software.
     * <p>
     * This can be somewhat subjective -- for example a checksum that is always part of the command format is probably
     * not an argument.
     */
    List<Argument> argumentList = new ArrayList<>();

    /**
     * From XTCE: Tells how to package this command.
     * <p>
     * May not be referred to in the EntryList of a SequenceContainer, CommandContainerSet/CommandContainer or another
     * MetaCommandContainer.
     * <p>
     * May be extended by another MetaCommand/CommandContainer.
     */
    CommandContainer commandContainer;

    MetaCommand baseMetaCommand;
    // assignment for inheritance
    List<ArgumentAssignment> argumentAssignmentList;

    /**
     * From XTCE: Some Command and Control Systems may require special user access or confirmations before transmitting
     * commands with certain levels. The level is inherited from the Base MetaCommand, or it overrides any in the
     * parent-chain if given here, however it should not go down in consequenceLevel.
     */
    private Significance defaultSignificance = null;

    /**
     * if command is abstract, it cannot be instantiated
     */
    boolean abstractCmd = false;

    List<TransmissionConstraint> transmissionConstraints = new ArrayList<>();

    /**
     * From XTCE A Command Verifier is a conditional check on the telemetry from a SpaceSystem that that provides
     * positive indication on the processing state of a command. There are eight different verifiers each associated
     * with difference states in command processing: TransferredToRange, TransferredFromRange, Received, Accepted,
     * Queued, Execution, Complete, and Failed. There may be multiple ‘complete’ verifiers. ‘Complete’ verifiers are
     * added to the Base MetaCommand ‘Complete’ verifier list. All others will override a verifier defined in a Base
     * MetaCommand
     * 
     * 
     * In Yamcs the verifier type is specified in the stage field.
     */
    private List<CommandVerifier> verifiers = new ArrayList<>();

    public MetaCommand(String name) {
        super(name);
    }

    /**
     * Set the command as abstract or non abstract. Abstract commands cannot be instantiated
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
     * returns the argument assignment list in relation to the inheritance - this is the list of arguments of the
     * parent(s) which are assigned when the inheritance takes place
     * 
     * returns null if there is no such argument
     * 
     * @return
     */
    public List<ArgumentAssignment> getArgumentAssignmentList() {
        if (argumentAssignmentList == null) {
            return null;
        }
        return Collections.unmodifiableList(argumentAssignmentList);
    }

    /**
     * returns the list of arguments of this command can be empty if the command doesn't have arguments
     * 
     * @return
     */
    public List<Argument> getArgumentList() {
        if (argumentList == null) {
            return null;
        }
        return Collections.unmodifiableList(argumentList);
    }

    /**
     * returns the list of all arguments including those inherited from the parent
     */
    public List<Argument> getEffectiveArgumentList() {
        // collect all the parents in a list so we can add the arguments starting from the top
        List<MetaCommand> mclist = getHierarchy();

        List<Argument> r = new ArrayList<>();
        for (int i = mclist.size() - 1; i >= 0; i--) {
            MetaCommand mc = mclist.get(i);
            if (mc.argumentList != null) {
                r.addAll(mc.argumentList);
            }
        }
        return r;
    }

    public List<ArgumentAssignment> getEffectiveArgumentAssignmentList() {
        List<MetaCommand> mclist = getHierarchy();

        List<ArgumentAssignment> r = new ArrayList<>();
        for (int i = mclist.size() - 1; i >= 0; i--) {
            MetaCommand mc = mclist.get(i);
            if (mc.argumentAssignmentList != null) {
                r.addAll(mc.argumentAssignmentList);
            }
        }

        return r;
    }

    private List<MetaCommand> getHierarchy() {
        List<MetaCommand> mcList = new ArrayList<>();
        MetaCommand mc = this;
        while (mc != null) {
            mcList.add(mc);
            mc = mc.getBaseMetaCommand();
        }
        return mcList;
    }

    /**
     * returns an argument based on name or null if it doesn't exist
     * <p>
     * The argument is only looked up in the current meta command, not in its parent.
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
     * Same as {@link #getArgument(String)} but looks up the argument also in the parent
     */
    public Argument getEffectiveArgument(String argumentName) {
        Argument arg = getArgument(argumentName);
        if (arg == null && baseMetaCommand != null) {
            arg = baseMetaCommand.getEffectiveArgument(argumentName);
        }
        return arg;
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

    /**
     * Use {@link #addTransmissionConstraint(TransmissionConstraint)} instead.
     */
    @Deprecated
    public void addTransmissionConstrain(TransmissionConstraint constraint) {
        addTransmissionConstraint(constraint);
    }

    public void addTransmissionConstraint(TransmissionConstraint constraint) {
        transmissionConstraints.add(constraint);
    }

    /**
     * Use {@link #getTransmissionConstraints(boolean)} instead.
     */
    @Deprecated
    public List<TransmissionConstraint> getTransmissionConstraintList() {
        return getTransmissionConstraints(false);
    }

    /**
     * Returns the list of transmission constraints (can be empty but not null).
     * 
     * @param includeBase
     *            if this MetaCommand has a base command, consider also transmission constraints up the hierarchy.
     *            Constraints are ordered from root to leaf, and in insertion order on each node.
     */
    public List<TransmissionConstraint> getTransmissionConstraints(boolean includeBase) {
        if (includeBase) {
            var result = new ArrayList<>(transmissionConstraints);
            var base = baseMetaCommand;
            while (base != null) {
                result.addAll(0, base.transmissionConstraints);
                base = base.baseMetaCommand;
            }
            return result;
        } else {
            return Collections.unmodifiableList(transmissionConstraints);
        }
    }

    public void addArgumentAssignment(ArgumentAssignment aa) {
        if (argumentAssignmentList == null) {
            argumentAssignmentList = new ArrayList<>();
        }
        argumentAssignmentList.add(aa);
    }

    /**
     * Use {@link MetaCommand#hasTransmissionConstraints(boolean)} instead.
     */
    @Deprecated
    public boolean hasTransmissionConstraints() {
        return hasTransmissionConstraints(false);
    }

    /**
     * Returns whether this MetaCommand has transmission constraints
     * 
     * @param includeBase
     *            if this MetaCommand has a base command, consider also transmission constraints up the hierarchy.
     */
    public boolean hasTransmissionConstraints(boolean includeBase) {
        var found = !transmissionConstraints.isEmpty();
        if (!found && includeBase) {
            var base = baseMetaCommand;
            while (base != null) {
                if (!base.transmissionConstraints.isEmpty()) {
                    found = true;
                    break;
                }
                base = base.baseMetaCommand;
            }
        }
        return found;
    }

    /**
     * returns the command significance either directly defined or inherited from the parent
     * <p>
     * Returns null of no significance is attached to the command
     */
    public Significance getEffectiveDefaultSignificance() {
        return defaultSignificance != null ? defaultSignificance
                : baseMetaCommand != null ? baseMetaCommand.getEffectiveDefaultSignificance()
                        : null;
    }

    public Significance getDefaultSignificance() {
        return defaultSignificance;
    }

    public void setDefaultSignificance(Significance defaultSignificance) {
        this.defaultSignificance = defaultSignificance;
    }

    public void addVerifier(CommandVerifier cmdVerifier) {
        verifiers.add(cmdVerifier);
    }

    /**
     * Use {@link #hasCommandVerifiers(boolean)} instead.
     */
    @Deprecated
    public boolean hasCommandVerifiers() {
        return hasCommandVerifiers(true);
    }

    /**
     * Returns whether this MetaCommand has verifiers
     * 
     * @param includeBase
     *            if this MetaCommand has a base command, consider also verifiers up the hierarchy.
     */
    public boolean hasCommandVerifiers(boolean includeBase) {
        var found = !verifiers.isEmpty();
        if (!found && includeBase) {
            var base = baseMetaCommand;
            while (base != null) {
                if (!base.verifiers.isEmpty()) {
                    found = true;
                    break;
                }
                base = base.baseMetaCommand;
            }
        }
        return found;
    }

    /**
     * Use {@link #getCommandVerifiers(boolean)} instead.
     */
    @Deprecated
    public List<CommandVerifier> getCommandVerifiers() {
        return Collections.unmodifiableList(verifiers);
    }

    /**
     * Returns the list of verifiers (can be empty but not null).
     * 
     * @param includeBase
     *            if this MetaCommand has a base command, consider also verifiers up the hierarchy. Verifiers are
     *            ordered from root to leaf, and in insertion order on each node.
     */
    public List<CommandVerifier> getCommandVerifiers(boolean includeBase) {
        if (includeBase) {
            var result = new ArrayList<>(verifiers);
            var base = baseMetaCommand;
            while (base != null) {
                result.addAll(0, base.verifiers);
                base = base.baseMetaCommand;
            }
            return result;
        } else {
            return Collections.unmodifiableList(verifiers);
        }
    }

    public void print(PrintStream out) {
        out.print("MetaCommand name: " + name + " abstract:" + abstractCmd);
        if (getAliasSet() != null) {
            out.print(", aliases: " + getAliasSet());
        }

        if (!transmissionConstraints.isEmpty()) {
            out.print(", TransmissionConstraints: ");
            out.print(transmissionConstraints.toString());
        }
        if (defaultSignificance != null) {
            out.print(", defaultSignificance: ");
            out.print(defaultSignificance.toString());
        }

        if (!verifiers.isEmpty()) {
            out.print(", Verifiers: ");
            out.print(verifiers.toString());
        }
        out.println();
        if (baseMetaCommand != null) {
            out.println("\t baseMetaCommand: " + baseMetaCommand.getName() + " with argument assignment:"
                    + argumentAssignmentList);
        }
        if (commandContainer != null) {
            commandContainer.print(out);
        }
    }

    public void setArgumentAssignmentList(List<ArgumentAssignment> argumentAssignmentList) {
        this.argumentAssignmentList = argumentAssignmentList;
    }
}
