CFDP Get Info
=============

Get info on one, multiple or all ongoing and/or finished/cancelled CFDP transfer::

    GET /api/cfdp/info

.. rubric:: Parameters 

transaction ids (array of integers)
    An optional list of CFDP transfer ids whereof the info is to be returned.

all (boolean)
    If set to ``True``, info for all current and past CFDP transfers is shown. If set to ``False``, only ongoing CFDP transfers are considered. Ignored if ``transaction ids`` is present. Defaults to ``True``. 

.. rubric:: Response
.. code-block:: json

    {
      "transfers": [ {
        "transferId": 1,
        "instanceName": "my_instance",
        "bucketName": "my_bucket",
        "objectName": "my_object",
        "remoteFilepath": "a/remote/path/filename",
        "uploadDownload": "upload",
        "completion": "49%",
        "state": "ongoing, stalled"
      },
      {
        "transferId": 2,
        "instanceName": "my_instance",
        "bucketName": "some_bucket",
        "objectName": "some_object",
        "remoteFilepath": "a/remote/path/other_filename",
        "uploadDownload": "download",
        "completion": "5%",
        "state": "finished, aborted"
      },
      {
        "transferId": 3,
        "instanceName": "my_instance",
        "bucketName": "some_bucket",
        "objectName": "some_object",
        "remoteFilepath": "a/remote/path/other_filename",
        "uploadDownload": "download",
        "completion": "100%",
        "state": "finished, completed"
      }]
    }

.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    enum TransferState {
      RUNNING = 1;
      PAUSED = 2;
      FAILED = 3;
      COMPLETED = 4;
    }

    enum TransferDirection {
      UPLOAD = 1;
      DOWNLOAD = 2;
    }

    message TransactionId {
      optional uint32 sequenceNumber = 1;
      optional uint64 initiatorEntity = 2;
    }

    message TransferInfo {
      optional uint32 id = 1; // unique identifier assigned by the CfdpService
      optional google.protobuf.Timestamp startTime = 2;
      optional TransferState state = 3;
      optional string bucket = 4;
      optional string objectName = 5;
      optional string remotePath = 6;
      optional TransferDirection direction = 7;
      optional uint64 totalSize = 8;
      optional uint64 sizeTransferred = 9;
      optional bool reliable = 10; //reliable == true --> class 2 transfer; reliable == false --> class 1 transfer
      optional string failureReason = 11; // in case the transaction has failed, this provides more information
      optional TransactionId transactionId = 12; // CFDP transaction id, for the incoming transfers it is assigned by the remote peer so therefore it might not be unique
    }
    
    message ListTransferrsResponse {
      repeated TransferInfo transfer = 1;
    }
