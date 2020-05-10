Create Transfer
===============

Create a CFDP transfer::

    POST /api/cfdp/{instance}/transfers


.. rubric:: Parameters

direction (string)
    **Required** One of ``UPLOAD`` or ``DOWNLOAD``.

bucket (string)
    **Required** The bucket containing the local Yamcs object.

objectName (string)
    **Required** The object name in Yamcs bucket storage. For ``UPLOAD`` transfers, this object must exist and is what Yamcs will transfer to the remote CFDP entity. For ``DOWNLOAD`` transfers, it refers to the object that Yamcs will write to when downloading from a remote CFDP entity.

remotePath (string)
    **Required** The path at the remote CFDP entity. Example: ``a/local/path/some_filename``.

uploadOptions (map)
    Configuration options specific to ``UPLOAD`` transfers.


.. rubric:: Upload Options

When creating a transfer of type ``Upload``, the ``uploadOptions`` parameter supports these additional sub-parameters:

reliable (boolean)
    Set to ``True`` if reliable (class 2) CFDP transfer should be used, otherwise unreliable (class 1). Default: ``False``.

overwrite (boolean)
    Set to ``True`` if an already existing destination should be overwritten. Default: ``True``.

createPath (boolean)
    Set to ``True`` if the destination path should be created if it does not exist. Default: ``True``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message CreateTransferRequest {
      message UploadOptions {
        optional bool overwrite = 1;
        optional bool createPath = 2;
        optional bool reliable = 3;
      }
      message DownloadOptions {
      }
    
      optional TransferDirection direction = 2;
      optional string bucket = 3;
      optional string objectName = 4;
      optional string remotePath = 5;
      optional DownloadOptions downloadOptions = 6;
      optional UploadOptions uploadOptions = 7;
    }
