Parameters Sheet
================

This sheet contains parameter information.

Recognised column names are:

``parameter name`` (required)
    The name of the parameter within the space system.

``data type`` (required)
    Reference to a data type define in the :doc:`DataTypes sheet <data-types>`.

``description``
    Textual description of the parameter. Should be one line.

``long description``
    Long textual description of the parameter. In Markdown format.

``namespace:<ALIAS>``
    Any numbers of namespace columns can be added using the prefix ``namespace:`` followed by the name of a namespace.

    This allows associating alternative names to parameters.

``initial value``
    Initial (default) value of this parameter. If present, this overrides any initial value set on the referenced ``data type``.

    .. include:: _includes/initial-value.rst

``flags``
   The only recognized flag is ``p`` which sets the parameter as persistent - that means its value will be saved and restored when the Yamcs restarts. For this to work, the realtime processor has to be configured (in processor.yaml) with ``persistParameters: true``
