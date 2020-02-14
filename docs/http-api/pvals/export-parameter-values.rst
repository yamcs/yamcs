Export Parameter Values
=======================

Export a CSV of archived parameter values::

    GET /api/archive/{instance}:exportParameterValues


.. rubric:: Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

norepeat (bool)
    Whether to filter out consecutive identical values. Default ``no``.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``.

extra (array of strings)
    Extra columns added to the CSV output:

    * ``raw``: Raw parameter values
    * ``monitoring``: Monitoring status

    Example: ``?extra=raw,monitoring``
