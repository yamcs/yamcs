Get Container Info
==================

Return the data for the given container::

    GET /api/mdb/:instance/containers/:namespace/:name


.. rubric:: Response
.. literalinclude:: _examples/get-container-info-output.json
    :language: json


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ContainerInfo {
      optional string name = 1;
      optional string qualifiedName = 2;
      optional string shortDescription = 3;
      optional string longDescription = 4;
      repeated yamcs.NamedObjectId alias = 5;
      optional int64 maxInterval = 6;
      optional int32 sizeInBits = 7;
      optional ContainerInfo baseContainer = 8;
      repeated ComparisonInfo restrictionCriteria = 9;
      repeated SequenceEntryInfo entry = 10;
    }
