List Processors
===============

List all processors, across all Yamcs instances::

    GET /api/processors

List all processors for the given Yamcs instance::

    GET /api/processors/:instance


.. rubric:: Parameters

type (string)
    Indicates the type of the processors to return. Can be either ``replay``, ``realtime`` or ``all``. Default: ``all``


.. rubric:: Response
.. code-block:: json

    {
      "processor" : [ {
        "instance" : "simulator",
        "name" : "realtime",
        "type" : "realtime",
        "creator" : "system",
        "hasCommanding" : true,
        "state" : "RUNNING"
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListProcessorsResponse {
      repeated yamcsManagement.ProcessorInfo processor = 1;
    }
