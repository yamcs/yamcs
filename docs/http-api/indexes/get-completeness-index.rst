Get Completeness Index
======================

Get the completeness index records for the given instance::

    GET /api/archive/{instance}/completeness-index


.. rubric:: Parameters

start (string)
    Filter the lower bound of the index entries. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the index entries. Specify a date string in ISO 8601 format.

limit (integer)
    | The maximum number of returned entries. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``1000``.
    | Note that in general it is advised to control the size of the response via ``mergeTime``, rather than via ``limit``.


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message IndexResponse {
      repeated IndexGroup group = 1;
      optional string continuationToken = 2;
    }
