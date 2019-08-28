List Tags
=========

List all tags for the given instance::

    GET /api/archive/:instance/tags


.. rubric:: Parameters

start (string)
    Filter the lower bound of the tag. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the tag. Specify a date string in ISO 8601 format.


.. rubric:: Example
.. code-block:: json

    {
      "tag" : [ {
        "id" : 1,
        "name" : "My annotation",
        "start" : 1449128432000,
        "stop" : 1449174255000,
        "description" : "blabla",
        "color" : "#ffc800"
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListTagsResponse {
      repeated yamcs.ArchiveTag tag = 1;
    }
