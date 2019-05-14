Delete Partitions
=================

Delete partitions from the parameter archive::

    GET /api/archive/:instance/parameterArchive/deletePartitions


.. rubric:: Parameters

start (string)
    Start with the partition that contains this timestamp. Specify a date string in ISO 8601 format.

stop (string)
    Stop with the partition that contains this timestamp. The stop partition will be removed as well. Specify a date string in ISO 8601 format.


.. rubric:: Response

Response is of type string and list the partitions that have been removed.
