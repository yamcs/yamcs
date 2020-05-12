Get Transfer
============

Get info on a specific CFDP transfer::

    GET /api/cfdp/{instance}/transfers/{id}


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message TransferInfo {
      //unique identifier assigned by the CfdpService
      optional uint32 id = 1;
      
      optional google.protobuf.Timestamp startTime = 2;
      optional TransferState state = 3;
    
      optional string bucket = 4;
      optional string objectName = 5;
    
      optional string remotePath = 6;
      optional TransferDirection direction = 7;
    
      optional uint64 totalSize = 8;
      optional uint64 sizeTransferred = 9;
      
      //reliable = true -> class 2 transfer
      //reliable = false -> class 1 transfer
      optional bool reliable = 10;
      
      //in case the transcation is failed, this provides more information
      optional string failureReason = 11;
    
      // CFDP transaction id;
      // for the incoming transfers it is assigned by the remote peer so therefore might not be unique
      optional TransactionId transactionId = 12;
    }
