Write Table Rows
================

Load data into a table::

     POST /api/archive/{instance}/tables/{table}:writeRows

Use HTTP header (anything else is not supported)::

    Content-Type: application/protobuf

The request body is a stream of ``Row`` messages as obtained by :doc:`read-table-rows`.

The table has to exist in order to load data into it.


.. rubric:: Response

The Response can be Protobuf or JSON depending on the ``Accept`` header of the request.

When the client is done streaming the server returns a successful response that contains the number of rows that were written:

.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message WriteRowsResponse {
       optional uint32 count = 1;
    }

As soon as the server detects an error with one of the written rows, it will forcefully close the connection and send back an early error message. The client should stop streaming and handle the error. Note that the erratic condition causes the connection to be closed even if the ``Keep-Alive`` request header was enabled.

The error response is of type ``ExceptionMessage`` and contains a detail message of type ``WriteRowsExceptionDetail`` that provides the number of rows that were successfully written by the client. The client can use this information to link the error message to a row (i.e. the bad row is at position ``count + 1`` of the stream).


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ExceptionMessage {
       optional string type = 1;
       optional string msg = 2;
       optional google.protobuf.Any detail = 3;
    }

    message WriteRowsExceptionDetail {
       optional uint32 count = 1;
    }

One possible error could be that the table has defined a (primary) key and one of the loaded rows contains no value for one of the columns of the key.
