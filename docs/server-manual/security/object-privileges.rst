Object Privileges
=================

An object privilege is the right to perform a particular action on an object. The object is assumed to be identifiable by a single string. The object may also be expressed as a regular expression, in which case Yamcs will perform pattern matching when doing authorization checks.

Command
    Allows to issue a specific command.
CommandHistory
    Allows access to the command history of a specific command.
ManageBucket
    Allow control over a specific bucket.
ReadAlgorithm
    Allows to read a specific algorithm.
ReadBucket
    Allow readonly access to a specific bucket.
ReadPacket
    Allows to read a particular packet.
ReadParameter
    Allows to read a particular parameter.
Stream
    Allow to read and emit to a specific stream.
WriteParameter
    Allows to set the value of a specific parameter.

.. note::

    Yamcs plugins may support additional object privileges.
