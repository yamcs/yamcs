Object Privileges
=================

An object privilege is the right to perform a particular action on an object. The object is assumed to be identifiable by a single string. The object may also be expressed as a regular expression, in which case Yamcs will perform pattern matching when doing authorization checks.

Command
    Allows to issue a specific command.

CommandHistory
    Allow access to the command history of a specific command.

ManageBucket
    Allow control over a specific :doc:`bucket <../data-management/buckets>`.

    A typical installation includes at least the buckets ``displays`` and ``stacks``.

ReadAlgorithm
    Allow to read a specific algorithm.

ReadBucket
    Allow readonly access to a specific :doc:`bucket <../data-management/buckets>`.

    A typical installation includes at least the buckets ``displays`` and ``stacks``.
ReadPacket
    Allow to read a specific packet.

ReadParameter
    Allow to read a specific parameter.

Stream
    Allow to read and emit to a specific stream.

WriteParameter
    Allows to set the value of a specific parameter.


.. note::

    Yamcs plugins may support additional object privileges.
