List Command Info
=================

List all commands defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance/commands


.. rubric:: Parameters

namespace (string)
    Include commands under the specified namespace only

recurse (bool)
    If a ``namespace`` is given, specifies whether to list commands of any nested sub systems. Default ``no``.

q (string)
    The search keywords. This support searching on namespace or name.


.. rubric:: Response
.. code-block:: json

    {
      "command" : [ {
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

    message ListCommandInfoResponse {
      repeated mdb.CommandInfo command = 1;
    }
