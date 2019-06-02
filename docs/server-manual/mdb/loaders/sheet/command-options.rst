CommandOptions Sheet
====================

This sheet defines two types of options for commands:

* transmission constraints - these are conditions that have to be met in order for the command to be sent.
* command significance - this is meant to flag commands that have a certain significance. Currently the significance is only used by the end user applications to raise the awareness of the operator when sending such command.

Command name
    The name of the command. Any entry starting with ``#`` is treated as a comment row

Transmission Constraints
    Constrains can be specified on multiple lines. All of them have to be met for the command to be allowed for transmission.

Constraint Timeout
    This refers to the left column. A command stays in the queue for that many milliseconds. If the constraint is not met, the command is rejected. 0 means that the command is rejected even before being added to the queue, if the constraint is not met.

Command Significance
    Significance level for commands. Depending on the configuration, an extra confirmation or certain privileges may be required to send commands of high significance. One of:

    - none
    - watch
    - warning
    - distress
    - critical
    - severe

Significance Reason
    A message that will be presented to the user explaining why the command is significant.
