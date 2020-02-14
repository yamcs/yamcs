Export Events
=============

Export a CSV of archived events::

    GET /api/archive/{instance}:exportEvents


.. rubric:: Parameters

severity (string)
    The minimum severity level of the events. One of ``info``, ``watch``, ``warning``, ``distress`` or ``severe``. Default: ``info``

q (string)
    Text to search for in the message.

source (array of strings)
    The source of the events. Names must match exactly. Both these notations are accepted:

    * ``?source=DataHandler,CustomAlgorithm``
    * ``?source[]=DataHandler&source[]=CustomAlgorithm``

start (string)
    Filter the lower bound of the event's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the event's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.
