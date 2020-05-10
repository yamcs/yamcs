CFDP Pause
==========

Pause one, multiple or all ongoing CFDP transfers:

    GET /api/cfdp/pause

.. rubric:: Parameters

transaction ids (array of integers)
    An optional list of ongoing CFDP transfer ids that are to be paused.

.. rubric:: Response
.. code-block:: json
 
    {
      "paused": [1, 3, 5]
    }
