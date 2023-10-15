Commands Sheet
==============

This sheet contains commands description, including arguments. General conventions:

* First line with a new 'Command name' starts a new command
* Second line after a new 'Command name' should contain the first command arguments
* Empty lines are only allowed between two commands.

The column names are:

``command name``
    The name of the command. Any entry starting with `#` is treated as a comment row.

``parent``
    Name of the parent command and position in bits.

    Can be specified starting with ``/`` for an absolute reference or with ``../`` for pointing to parent space system.
    
    A suffix ``:x`` means that the arguments in this container start at position ``x`` (in bits) relative to the top-most container.
    
    Currently there is a problem for containers that have no argument: the bit position does not apply to children and has to be repeated.

``argument assignment``
    ``name1=value1;name2=value2`` where ``name1`` and ``name2`` are the names of arguments which are assigned when the inheritance takes place.

``flags``
    For commands: ``A`` is abstract. For arguments: ``L`` is little endian.

``argument name``
    From this column on, most of the cells are valid for arguments only. These have to be defined on a new row after the command. The exceptions are: ``description``, ``long description`` and aliases.

``position``
    Relative position to the previous argument. Default: 0

``data type``
    Reference to a data type define in the :doc:`DataTypes sheet <data-types>`.

    Or a value of the form ``FixedValue(n)`` where ``n`` is the size in bits. This can be used to fill the packet with a value without requiring an argument.

``default value``
    Default value. If ``data type`` is a ``FixedValue``, this has to contain the value in hexadecimal.
    
    Note that when the size of the argument is not an integer number of bytes (which is how hexadecimal binary strings are specified), the most significant bits are ignored.

``range low``
    The value of the argument cannot be smaller than this. For strings and binary arguments this means the minimum length in characters, respectively bytes.

``range high``
    The value of the argument cannot be higher than this. Only applies to numbers. For strings and binary arguments this means the minimum length in characters, respectively bytes.

``description``
    Optional free text description. Should be one line.

``long description``
    Long textual description. In Markdown format.
