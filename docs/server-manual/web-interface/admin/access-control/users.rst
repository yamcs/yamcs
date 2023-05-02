Users
=====

Page that lists users *known* to Yamcs. There are two categories of users:

Internal users
    Users whose identity is managed directly by Yamcs using a password hash stored in the Yamcs database.

External users
    Users whose identity is managed by an external system, such as an LDAP server or Keycloak server.

    When an external user logs in to Yamcs, that user's username and metadata of interest (display name, email) is synced into the Yamcs database.

.. note::

    Some installations make use of :doc:`../../../security/authmodules/yaml`. While this uses a local :file:`etc/users.yaml` configuration file, it counts as an external user because the password verification is managed with YAML instead of the Yamcs database.


.. rubric:: Converting a user from external to internal

#. Open the user detail page.
#. Delete entries under the rubric **External Identities**.
#. It is now possible to set or change the user password.


.. rubric:: Block a user

#. Open the user detail page, and click `EDIT USER`.
#. Untoggle the Active slider.


.. rubric:: Promote a user to administrator

#. Open the user detail page, and click `EDIT USER`.
#. Toggle the superuser slider.
