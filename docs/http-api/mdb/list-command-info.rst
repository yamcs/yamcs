List Command Info
=================

List all commands defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/{instance}/commands


.. rubric:: Parameters

q (string)
    The search keywords. This supports searching on namespace or name.

system (string)
    List only direct child sub-systems or commands of the specified system. For example when querying the system "/a" against an MDB with commands "/a/b/c" and "/a/c", the result returns the sub system "/a/b" and the command "/a/c".

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned commands per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

When ``system`` and ``q`` are used together, matching commands at any depth are returned, starting from the specified space system.

.. rubric:: Response
.. code-block:: json

    {
      "commands" : [ {
        "name": "SWITCH_VOLTAGE_ON",
        "qualifiedName" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_ON",
        "alias" : [ {
          "name" : "SIMULATOR_SWITCH_VOLTAGE_ON",
          "namespace" : "MDB:OPS Name"
        }, {
          "name" : "SWITCH_VOLTAGE_ON",
          "namespace" : "/YSS/SIMULATOR"
        } ],
        "baseCommand" : {
          "name": "SIM_TC",
          "qualifiedName" : "/YSS/SIMULATOR/SIM_TC"
        },
        "abstract" : false,
        "argument" : [ {
          "name" : "voltage_num",
          "description" : "voltage number to switch on",
          "type" : "integer",
          "unitSet" : [ {
            "unit" : "V"
          } ]
        } ],
        "argumentAssignment" : [ {
          "name" : "packet-id",
          "value" : "1"
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListCommandsResponse {
      repeated mdb.CommandInfo commands = 1;
    }
