CFDP Cancel
===========

Cancel one, more or all ongoing CFDP transfers::
 
    POST /api/cfdp/cancel

The ongoing transfers will be aborted, the partially uploaded/downloaded files are retained.

.. rubric:: Parameters

transaction ids (array of integers)
    An optional list of CFDP transfer ids that have to be cancelled (if not cancelled/finished already). If this parameter is absent, all ongoing CFDP transfers are cancelled.

.. rubric:: Response
.. code-block:: json

    {
      "cancelledTransfers": [3, 5, 9]
    } 
