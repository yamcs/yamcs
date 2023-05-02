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
