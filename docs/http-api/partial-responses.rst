Partial Responses
=================

To reduce the size of an HTTP response message, it is possible to restrict returned fields by providing the query parameter ``fields``, or alternatively by setting the HTTP header ``X-Yamcs-Fields``. This is also called a `field mask`.

Field names are applicable to the top-level response message, and multiple fields can be separated by commas. Methods that return a list of messages apply the mask to each of the listed resources. Field paths can be of arbitrary depth separated by dots. Only the last part can refer to a repeated field.

Some examples:

Return information on the `simulator` instance, but include only the ``name`` and ``state`` fields:

.. code-block::

    curl 'localhost:8090/api/instances/simulator?fields=name,state'

Return a list of all instances, but include only the ``name`` and ``state`` fields:

.. code-block::

    curl 'localhost:8090/api/instances?fields=name,state'
