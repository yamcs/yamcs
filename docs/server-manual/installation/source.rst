Installing from Source
======================

Yamcs can be installed from source. This enables you to make customizations to the code base.

The Yamcs source code consists of two main chunks:

* The server daemon, written in Java, split over several Maven modules.
* The hosted web interface, written in Typescript, under the ``yamcs-web`` directory.


Obtaining Source Code
---------------------

Source code distributions are released together with each binary release. You can find past and current distributions in our online `release archive <https://yamcs.org/downloads/archive/>`_.

You may also build Yamcs directly from a clone of the source code. Yamcs source code is managed with ``git`` and is hosted on GitHub:

.. code-block:: text

    git clone https://github.com/yamcs/yamcs.git
    cd yamcs

By default this clone will provide you the latest development source code. If you already have a local clone and wish to update it, use:

.. code-block:: text

    git pull --rebase

This will update your local branch while giving you a chance to *rebase* your changes on top of the latest code.

Each Yamcs release has a git tag in the format ``yamcs-x.y.z``. To list all tags that are available in your clone, use ``git tag``:

.. code-block:: text

    git tag

Use ``git checkout`` to switch your clone to a specific release that you want to build. For example:

.. code-block:: text

    git checkout yamcs-4.2.0

To learn more about git, refer to this free book: https://git-scm.com/book/


Build Requirements
------------------

* Yamcs uses Java 8 language features. A **Java Development Kit** version 8 or higher is required. Use any JDK implementation, e.g.:

  * OpenJDK: https://openjdk.java.net
  * Oracle JDK: https://www.oracle.com/technetwork/java/javase/downloads/index.html

* **Maven** is used to manage the Java build lifecycle: https://maven.apache.org

* **npm** is needed to compile the web interface sources. We find the simplest way to install npm is to install a node.js distribution from https://github.com/nodesource/distributions.

* **yarn** must be installed in addition to npm. The sources for the web interface make use of *yarn workspaces* to interlink multiple projects. Once ``npm`` is installed, you can install yarn using npm:

  .. code:: text

      npm -g install yarn

* **make** is used to make it easier to work with other build tools. Find GNU Make at http://www.gnu.org/software/make/

* Yamcs can be built on Windows, Linux or macOS, however we currently only support building on **Linux**. Other platforms may require tweaks not part of this guide.

* Connectivity to Internet is required to fetch external dependencies. These dependencies are cached for later reuse.


Optional Build Requirements
---------------------------

Some source files are generated from description files. We commit the generated sources to our development tree so they are automatically included in all source packages. If you want to regenerate these files you need these additional tools:

* **javacc** to generate java parsers from a ``.cc`` grammar specification.

* **protoc** to generate java sources from ``.proto`` Protobuf files.


Build Yamcs
-----------

Compile and package source files. This generates ``jar`` files as well as a bundled binary ``tar.gz`` distribution.

.. code:: text

    make


Install Yamcs
-------------

Install Yamcs locally to ``/usr/local/yamcs``:

.. code:: text

    make install

Depending on your system setup, this may require sudo privileges.

You may want to override the install prefix. For example:

.. code:: text

    make install PREFIX=/opt

If you intend to install Yamcs on another machine, use the binary ``tar.gz`` distribution generated under ``distribution/target``.

.. warning::

    Be careful when installing with prefix ``/opt``. If you have previously installed Yamcs from an RPM or Debian distribution, you may cause chaos in that directory.
