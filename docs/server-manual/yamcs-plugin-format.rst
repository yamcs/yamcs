Yamcs Plugin Format
===================

Yamcs has a simple plugin system that facilitates hooking into internals. The main advantages is that it allows to trigger custom code on server-start, which makes it an ideal place for programmatic customizations.

For example, you could use a plugin to dynamically add services without even needing to write them in YAML. Or you could use a plugin to read and validate some custom configuration file that is shared by multiple of your components. Or maybe you want to add your own custom HTTP and WebSocket calls to the API.

The following is a detailed specification of how Yamcs plugins should be packaged. If you want the short instructions, just implement :javadoc:`org.yamcs.Plugin` and add this execution to the ``pom.xml`` of your Yamcs Maven project. Then everything will be derived automatically:

.. code-block:: xml

    <plugin>
      <groupId>org.yamcs</groupId>
      <artifactId>yamcs-maven-plugin</artifactId>
      <!--version>...</version-->
      <executions>
        <execution>
          <goals>
            <goal>detect</goal>
          </goals>
        </execution>
      </executions>
    </plugin>


Main configuration file
-----------------------

Yamcs plugins should be packaged inside regular jar files. You can have as many plugins inside a jar as you want. Your jar file must contain the following file in its classpath:

    /META-INF/services/org.yamcs.Plugin

The content of this file must list the class names of all the plugins in your jar (one on each line). So for instance if you want to register your plugin ``com.example.MyPlugin``, then the contents of the file ``org.yamcs.Plugin`` must be simply:

.. code-block:: text

    com.example.MyPlugin        

With this setup, Yamcs will find your plugin and hook it into its lifecycle.


Plugin metadata
---------------

In addition to the file ``/META-INF/services/org.yamcs.Plugin``, you must also add the following file to your classpath:

    /META-INF/yamcs/com.example.MyPlugin/plugin.properties

Replace ``com.example.MyPlugin`` with the class name of your own plugin. The file ``plugin.properties`` supports the following key value pairs:

.. code-block:: properties
    
    # REQUIRED. A short identifier for your plugin
    name=my-plugin

    # REQUIRED. A version number for your plugin
    version=1.0.0

    # Optional: freeform description (no markup)
    description=Example

    # Optional: your organization name
    organization=Example

    # Optional: your organization's URL
    organizationUrl=https://example.com

    # Optional: when your plugin package was generated (ISO 8601)
    generated=


All these properties are used by Yamcs as metadata for potential integration in APIs and UIs.
