Issue Command
=============

Issue a new command of the given type::

    POST /api/processors/:instance/:processor/commands/:namespace/:name


After validating the input parameters, the command will be added to the appropriate command queue for further dispatch.

.. rubric:: Parameters

origin (string)
    The origin of the command. Typically a hostname.

sequenceNumber (integer)
    The sequence number as specified by the origin. This gets communicated back in command history and command queue entries, thereby allowing clients to map local with remote command identities.

dryRun (bool)
    Whether a response will be returned without actually issuing the command. This is useful when debugging commands. Default ``no``

assignment (array of string pairs)
    The name/value assignments for this command.

comment (string)
    Comment attached to this command.


.. rubric:: Example
.. code-block:: json

    {
      "sequenceNumber" : 1,
      "origin" : "my-machine",
      "assignment" : [ {
        "name": "voltage_num",
        "value": "3"
      } ],
      "dryRun" : true
    }


.. rubric:: Response
.. code-block:: json

    {
      "queue" : "default",
      "source" : "SWITCH_VOLTAGE_ON(voltage_num: 3)",
      "hex" : "1864C000000000000000006A0000000103",
      "binary" : "GGTAAAAAAAAAAABqAAAAAQM="
    }

The binary is encoded in Base64 format.

.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message IssueCommandRequest {
      message Assignment {
        optional string name = 1;
        optional string value = 2;
      }
      repeated Assignment assignment = 1;
      optional string origin = 2;
      optional int32 sequenceNumber = 3;
      optional bool dryRun = 4;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message IssueCommandResponse {
      optional string queue = 1;
      optional string source = 2;
      optional string hex = 3;
      optional bytes binary = 4;
    }
