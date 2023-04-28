Parameters Sheet
================

This sheet contains parameter information.

Recognised column names are:

``parameter name`` (required)
    The name of the parameter within the space system.

``data type`` (required)
    Reference to a data type define in the :doc:`DataTypes sheet <data-types>`.

``description``
    Textual description of the parameter.

``namespace:<ALIAS>``
    Any numbers of namespace columns can be added using the prefix ``namespace:`` followed by the name of a namespace.

    This allows associating alternative names to parameters.

``initial value``
    Initial (default) value of this parameter. If present, this overrides any initial value set on the referenced ``data type``.

    .. include:: _includes/initial-value.rst
