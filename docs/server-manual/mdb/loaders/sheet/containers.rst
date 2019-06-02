Containers Sheet
================

The sheet contains description of the content of the container (packet). As per XTCE, a container is a structure describing a binary chunk of data composed of multiple entries.

A container can inherit from other container - meaning that it takes all entries from the parent and it adds some more. It can have two types of entries:

* parameters
* other containers (this is called aggregation)

General conventions:

* first line with a new 'container name' starts a new packet
* second line after a new 'container name' should contain the first measurement
* empty lines are only allowed between two packets

Comment lines starting with ``#`` on the first column can appear everywhere and are ignored.

* | **container name**

  | The relative name of the packet inside the space system

* | **parent**

  | Parent container and position in bits where the subcontainer starts, for example  ``PARENT_CONTAINER:64``. If position in bits is not specified, the default position is to start from the last parameter in the parent. If parent is not specified, either the container is the root, or it can be used as part of another container in aggregation.

* | **condition**

  | Inheritance condition, usually specifies a switch within the parent which activates this child, for example `MID=0x101` There are currently three forms supported:

    * Simple condition:  ``Parameter==value``
    * Condition list:  ``Parameter==value;Parameter2==value2`` - all conditions must be true
    * Boolean condition: ``op(epx1;exp2;...;expn)``

      * op is '&' (AND) or '|' (OR)
      * expi is a boolean expression or a simple condition

  | Currently the only supported conditions are on the parameters of the parent container. This cover the usual case where the parent defines a header and the inheritance condition is based on paraemters from the header.
