User
====

Get information on the authenticated user::

    GET /api/user


.. ruburic:: Example
.. code-block:: json

    {
      "login": "admin",
      "superuser": true
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message UserInfo {
      optional string name = 17;
      optional string displayName = 18;
      optional string email = 19;
      optional bool active = 16;
      optional bool superuser = 13;
      optional UserInfo createdBy = 20;
      optional google.protobuf.Timestamp creationTime = 14;
      optional google.protobuf.Timestamp confirmationTime = 21;
      optional google.protobuf.Timestamp lastLoginTime = 15;
      repeated string systemPrivilege = 11;
      repeated ObjectPrivilegeInfo objectPrivilege = 12;
      repeated GroupInfo groups = 22;
      repeated ExternalIdentityInfo identities = 23;
      repeated RoleInfo roles = 24;
    }
