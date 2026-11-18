Command Definitions
===================

A command is a message sent from Yamcs to a spacecraft or other remote system instructing it to perform a particular action or set of actions.

A command is defined by a name and a set of named arguments, the arguments are of specified data types.

Similar with the telemetry containers, the commands also support inheritance. A command inheriting another command, inherits all its parent arguments, can define certain fixed values for those and can add additional arguments.

Traditionally, the commands sent to spacecrafts are encoded into binary packets to save bandwith. Together with the command name and its arguments, the MDB defines how to compose the binary packet.


The MDB contains other optional characteristics for commands:

- Command Significance - can be used to indicate the relative importance or urgency of a command. That allows the user interface applications to alert the user. Yamcs can also use the significance to allow users with elevated privileges to send them. 
- Transmission Constraints - can be used to specify some conditions that have to be valid in order to send a command.
- Command Verification - can be used to verify the command execution after the command has been sent.

PUS 1 verifier inputs
---------------------

``org.yamcs.pus.Pus1Verifier`` retains the upstream positional contract for its first five
inputs: sent APID, sent sequence count, received APID, received sequence count, and report
subtype. Additional inputs are identified by their ``inputName`` and can be ordered freely
after these five inputs. ``reportType`` identifies the incoming PUS service type.

Sent and received APIDs are compared using their raw wire values when available,
so calibration does not change packet identity. Inputs without raw values, such
as command-history attributes, use their integer engineering value or enumeration
code instead.

To supply an optional acknowledgement-flags aggregate from a command argument, add the
following entries to the verifier algorithm's ``InputSet``, after the five positional inputs:

.. code-block:: xml

    <InputParameterInstanceRef parameterRef="/PUS/type" inputName="reportType"/>
    <InputParameterInstanceRef parameterRef="/yamcs/cmd/arg/my_ack_flags" inputName="ackFlags"/>

Replace ``/PUS/type`` with the telemetry parameter holding the PUS service type and
``my_ack_flags`` with the actual command argument name. Yamcs resolves the special
``/yamcs/cmd/arg/`` reference to the current command's argument value. The ``ackFlags``
alias is what the verifier uses; no argument path needs to be passed to its constructor.

By default the aggregate's boolean members are ``pus_acceptance_flag`` (stage 1),
``pus_start_exec_flag`` (stage 3), ``pus_progress_exec_flag`` (stage 5), and
``pus_completion_flag`` (stage 7).
The existing constructor ``ackFlags`` map can override
these stage-to-member names. A false boolean returns no verification result for that stage.
An absent input, missing or unset aggregate member, or non-boolean aggregate member leaves
normal verification enabled.

Only aggregate values are interpreted. Non-aggregate inputs, including integers and
booleans, leave normal verification enabled.

Previously, any aggregate in the sixth input position was treated as acknowledgement flags.
Such configurations must now explicitly set ``inputName="ackFlags"``. Error-code aggregates
and other optional inputs no longer trigger acknowledgement-flag lookup.
