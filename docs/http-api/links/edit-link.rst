Edit Link
=========

Edit a link::

    PATCH /api/links/:instance/:name


.. rubric:: Parameters

state (string)
    The state of the link. Either ``enabled`` or ``disabled``.

The same parameters can also be specified in the request body. In case both query string parameters and body parameters are specified, they are merged with priority being given to query string parameters.


.. rubric:: Example

Enable a link:

.. code-block:: json

    {
      "state" : "enabled"
    }

Disable a link:

.. code-block:: json

    {
      "state" : "disabled"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message EditLinkRequest {
      optional string state = 1;
    }
