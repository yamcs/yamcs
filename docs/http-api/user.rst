User
====

Get information on the authenticated user::

    GET /api/user


Response
--------

.. code-block:: json

    {
      "login": "admin",
      "superuser": true
    }


Alternative Media Types
-----------------------

Protobuf
^^^^^^^^

Use HTTP header::

    Accept: application/protobuf

Response is of type:

.. code-block:: proto
    :caption: yamcsManagement/yamcsManagement.proto

    message UserInfo {
      optional string login = 1;
      repeated ClientInfo clientInfo = 2;
      repeated string systemPrivilege = 11;
      repeated ObjectPrivilegeInfo objectPrivilege = 12;
      optional bool superuser = 13;
    }
