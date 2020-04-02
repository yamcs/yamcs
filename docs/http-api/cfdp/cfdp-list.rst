CFDP List
=========

Get the contents of a path on the remote CFDP entity, using the CFDP protocol::

    GET /api/cfdp/list

.. rubric:: Parameters

target(string)
    **Required** The target path at the remote CFDP entity.

.. rubric:: Response
.. code-block:: json

    {
      "remotePath": "a/remote/path",
      "items": [ { 
        "name": "a_file",
        "isDirectory": false
      },
      {
        "name": "a_directory",
        "isDirectory": true
      } ]
    } 

.. rubric:: Response
.. code-block:: proto

    message RemoteFile {
      required string filepath = 1;
      required bool isDirectory = 2;
    }

    message ListRemoteFilesResponse {
      required string remotePath = 1;
      repeated RemoteFile filepaths = 2;
    }
