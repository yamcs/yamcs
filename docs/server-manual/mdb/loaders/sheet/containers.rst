Containers Sheet
================

The sheet contains description of the content of the container (packet). As per XTCE, a container is a structure describing a binary chunk of data composed of multiple entries.

A container can inherit from other container meaning that it takes all entries from the parent, and add some more. A container can have two types of entries:

* parameters
* other containers (this is called aggregation)

General conventions:

* first line with a new ``container name`` starts a new container
* second line after a new ``container name`` should contain the first entry
* empty lines are only allowed between two containers

These are the column names:

``container name``
    The name of the container within the space system.

``parent``
    Parent container and position in bits where the subcontainer starts, for example ``PARENT_CONTAINER:64``. If the position in bits is not specified, the default position is to start from the last parameter in the parent. If parent is not specified, either the container is the root, or it can be used as part of another container in aggregation.

``condition``
    Inheritance condition. This specifies a switch within the parent which activates this child container, for example ``MID=0x101``. There are currently three condition forms supported:

    * Simple condition: ``Parameter==value``
    * Condition list: ``Parameter==value;Parameter2==value2``. All conditions must be true.
    * Boolean condition: ``&(epx1;exp2;...;expn)`` for an AND expression, or ``|(exp1;exp2;...;expn)`` for an OR expression. Nested expressions are either other boolean conditions or a simple condition.

    Currently the only supported conditions are on the parameters of the parent container. This cover the usual case where the parent defines a header and the inheritance condition is based on parameters from the header.

``flags``
    Optional flags.

    ``a``
        Use this container as archive partition. In the Archive Browser this will appear as a line, and it will be more efficient to filter the retrieval on this container.

``entry``
    A reference to a parameter, or a container without parent.

``position``
    Position of the entry. Could be relative to the previous entry or absolute (relative to the beginning of the packet).

    ``r:<n>``
        Position is relative to the previous entry separated by ``<n>`` bits.
    ``a:<n>``
        Position is absolute. ``<n>`` is the number of bits from the beginning of the packet.

     ``<n>`` is equivalent to ``r:<n>``.

     If unset, the default is ``r:0``, meaning the entry directly follows the preceding entry.

``size in bits``
    Only for containers (and not for parameter entries). If set, this represents the size of the container. Otherwise, the size is derived from the entries in the container.

    For example if the container contains some fillers at the end, this entry can be used to enforce the size such that it is not needed to add an artificial parameter. Note that the size matters only if the container is used as part of another container. Either inherited from or in aggregation.

``expected interval``
    Expected interval in milliseconds. If set then all parameters extracted from this container have an expiration time set to this interval multiplied with a configurable constant. See the option :ref:`expirationTolerance <expirationTolerance>` in :file:`etc/processor.yaml`.

``description``
    Textual description of the container. Should be one line.

``long description``
    Long textual description of the container. In Markdown format.

``namespace:<ALIAS>``
    Any numbers of namespace columns can be added using the prefix ``namespace:`` followed by the name of a namespace.

    This allows associating alternative names to containers.
