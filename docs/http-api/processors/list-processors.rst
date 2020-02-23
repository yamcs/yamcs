List Processors
===============

List all processors, across all Yamcs instances::

    GET /api/processors


.. rubric:: Parameters

instance (string)
    Return only processors of the specified instance

type (string)
    Indicates the type of the processors to return. Can be either ``replay``, ``realtime`` or ``all``. Default: ``all``


.. rubric:: Response
.. code-block:: json

    {
      "processors" : [ {
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
      repeated yamcsManagement.ProcessorInfo processors = 1;
    }
