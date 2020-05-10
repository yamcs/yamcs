CFDP Resume
===========

Resume one, multiple or all ongoing but paused CFDP transfers::

    GRT api/cfdp/resume

.. rubric:: Parameters

transaction ids (array of integers)
    An optional list of paused ongoing CFDP transfer ids that are to be resumed.

.. rubric:: Response
.. code-block:: json

    {
      "resumed": [1, 3, 5]
    }
