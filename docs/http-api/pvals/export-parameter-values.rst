Export Parameter Values
=======================

Export a CSV of archived parameter values::

    GET /api/archive/{instance}:exportParameterValues


.. rubric:: Query Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

parameters (array of strings)
    The parameters to add to the export.

extra (array of strings)
    Extra columns added to the CSV output:

    * ``raw``: Raw parameter values
    * ``monitoring``: Monitoring status

    Example: ``?extra=raw,monitoring``
