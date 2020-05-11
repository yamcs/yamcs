List Transfers
==============

List transfers::

    GET /api/cfdp/{instance}/transfers


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListTransfersResponse {
      repeated TransferInfo transfers = 1;
    }
