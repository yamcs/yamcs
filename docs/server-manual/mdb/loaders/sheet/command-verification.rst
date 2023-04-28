CommandVerification Sheet
=========================

This sheet defines how a command shall be verified once it has been sent for execution.

The transmission/execution of a command usual goes through multiple stages and a verifier can be associated to each stage.

Each verifier runs within a defined time window which can be relative to the release of the command or to the completion of the previous verifier. The verifiers have three possible outcomes:

* | **OK**
  | the stage has been passed successfully.
* | **NOK**
  | the stage verification has failed (for example there was an error on-board when executing the command, or the uplink was not activated).
* | **timeout**
  | the condition could not be verified within the defined time interval.

For each verifier it has to be defined what happens for each of the three outputs.

``Command name``
    The command relative name as defined in the Command sheet. Referencing commands from other subsystems is not supported.

``CmdVerifier Stage``
    Any name for a stage is accepted but XTCE defines the following ones:

    * TransferredToRange
    * SentFromRange
    * Received
    * Accepted
    * Queued
    * Execution
    * Complete
    * Failed

    Yamcs interprets these as strings without any special semantics. If special actions (like declaring the command as completed) are required for Complete or Failed, they have to be configured in ``OnSuccess``/``OnFail``/``OnTimeout`` columns. By default command history events with the name ``Verification_<stage>`` are generated.

``CmdVerifier Type``
    Supported types are:

    * ``container``: the command is considered verified when the container is received. Note that this cannot generate a Fail (NOK) condition - it's either OK if the container is received in the timewindow or timeout if the container is not received.
    * ``algorithm``: the result of the algorithm run is used as the output of the verifier. If the algorithm is not run (because it gets no inputs) or returns null, then the timeout condition applies

``CmdVerifier Text``
    Depending on the type:

    * ``container``: is the name of the container from the Containers sheet. Reference to containers from other space systems is not supported.
    * ``algorithm``: is the name of the algorithm from the Algorithms sheet. Reference to algorithms from other space systems is not supported.

``Time Check Window``
    start,stop in milliseconds defines when the verifier starts checking the command and when it stops.

``checkWindow is relative to``
    * ``LastVerifier`` (default): the start,stop in the window definition are relative to the end of the previous verifier. If there is no previous verifier, the start,stop are relative to the command release time. If the previous verifier ends with timeout, this verifier will also timeout without checking anything.
    * ``CommandRelease``: the start,stop in the window definition are relative to the command release.

``OnSuccess``
    Defines what happens when the verification returns true. It has to be one of:

    * ``SUCCESS``: command considered completed successful (``CommandComplete`` event is generated)
    * ``FAIL``:  ``CommandFailed`` event is generated
    * none (default): only a ``Verification_<stage>`` event is generated without an effect on the final execution status of the command.

``OnFail``
    Same as ``OnSuccess`` but the event is generated in case the verifier returns false.

``OnTimeout``
    Same as ``OnSuccess`` but the event is generated in case the verifier times out.
