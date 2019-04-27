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
        "parameterCount" : 3,
        "containerCount" : 1,
        "commandCount" : 1,
        "sub" : [ {
          "name" : "SIMULATOR",
          "qualifiedName" : "/YSS/SIMULATOR",
          "version" : "1.0",
          "parameterCount" : 59,
          "containerCount" : 9,
          "commandCount" : 8,
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


.. rubric:: Java-serialized XtceDb

Use HTTP header::

    Accept: application/x-java-serialized-object

This returns a full java-serialized binary dump of the :javadoc:`~org.yamcs.xtce.XtceDb` object for this instance. You will need a dependency on the LGPL ``yamcs-api`` jar if you want to interpret it.
