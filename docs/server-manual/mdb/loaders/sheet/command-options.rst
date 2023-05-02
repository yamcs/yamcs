CommandOptions Sheet
====================

This sheet defines two types of options for commands:

* Transmission constraints: these are conditions that have to be met in order for the command to be sent.
* Command significance: this flags commands that are of **significance**. The significance can be used in end-user applications to raise the user's awareness before sending a command.

The column names are:

``command name`` (required)
    The name of a command. Any entry starting with ``#`` is treated as a comment row

``transmission constraints``
    Constraints can be specified on multiple lines. All of them have to be met for the command to be allowed for transmission.

``constraint timeout``
    This refers to the left column. A command stays in the queue for that many milliseconds. If the constraint is not met, the command is rejected. 0 means that the command is rejected even before being added to the queue, if the constraint is not met.

``command significance``
    Significance level for commands. Depending on the configuration, an extra confirmation or certain privileges may be required to send commands of high significance. One of:

    - ``none``
    - ``watch``
    - ``warning``
    - ``distress``
    - ``critical``
    - ``severe``

``significance reason``
    A message that will be presented to the user explaining why the command is significant.

Unlike with other sheets, the column names are not currently enforced. Instead the column order must match this description.
