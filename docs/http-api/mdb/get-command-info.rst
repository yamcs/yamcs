Get Command Info
================

Return the data for the given command::

    GET /api/mdb/:instance/commands/:namespace/:name


.. rubric:: Response
.. literalinclude:: _examples/get-command-info-output.json
    :language: json

.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message CommandInfo {
      optional string name = 1;
      optional string qualifiedName = 2;
      optional string shortDescription = 3;
      optional string longDescription = 4;
      repeated yamcs.NamedObjectId alias = 5;
      optional CommandInfo baseCommand = 6;
      optional bool abstract = 7;
      repeated ArgumentInfo argument = 8;
      repeated ArgumentAssignmentInfo argumentAssignment = 9;
      optional SignificanceInfo significance = 10;
      repeated TransmissionConstraintInfo constraint = 11;
    }
