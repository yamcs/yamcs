Installing Using a Linux Repository
===================================

Yamcs is distributed via yum and APT. Configure the Yamcs repository appropriate to your distribution following the `repository instructions </downloads/>`_.

RPM (RHEL, Fedora, CentOS)
^^^^^^^^^^^^^^^^^^^^^^^^^^

Install via ``dnf`` (or ``yum`` on older distributions)

.. code-block:: shell

    dnf check-update
    sudo dnf install yamcs

RPM (SLE, openSUSE)
^^^^^^^^^^^^^^^^^^^

.. code-block:: shell

    sudo zypper refresh
    sudo zypper install yamcs

APT (Debian, Ubuntu)
^^^^^^^^^^^^^^^^^^^^

.. code-block:: shell

    sudo apt-get update
    sudo apt-get install yamcs
