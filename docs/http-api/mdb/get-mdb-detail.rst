Get MDB Detail
==============

Get data on a the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance


.. rubric:: Response
.. code-block:: json

    {
      "configName" : "landing",
      "name" : "",
      "spaceSystem" : [ {
        "name" : "YSS",
        "qualifiedName" : "/YSS",
        "version" : "1.2",
        "sub" : [ {
          "name" : "SIMULATOR",
          "qualifiedName" : "/YSS/SIMULATOR",
          "version" : "1.0",
          "history" : [ {
            "version" : "1.3",
            "date" : "21-June-2020",
            "message" : "modified this and that"
          } ]
        } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message MissionDatabase {
      required string configName = 1;
      required string name = 2;
      optional string version = 3;
      repeated SpaceSystemInfo spaceSystem = 4;
    }
