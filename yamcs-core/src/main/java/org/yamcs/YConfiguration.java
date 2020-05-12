package org.yamcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A configuration object is a wrapper around a Map&ltString, Object&gt which keeps track to a parent and its original
 * file (if any).
 * 
 * This class loads yamcs configurations. There are a number of "subsystems", each using a corresponding subsystem.yaml
 * file
 *
 * Configuration files are looked up in this order:
 * <ol>
 * <li>in the prefix/file.yaml via the classpath if the prefix is set in the setup method (used in the unittests)
 * <li>in the userConfigDirectory .yamcs/etc/file.yaml
 * <li>in the file.yaml via the classpath..
 * </ol>
 *
 * @author nm
 */
@SuppressWarnings("rawtypes")
public class YConfiguration {

    public static File configDirectory; // This is used in client tools to overwrite
    static YConfigurationResolver resolver = new DefaultConfigurationResolver();

    private static Map<String, YConfiguration> configurations = new HashMap<>();
    static Logger log = LoggerFactory.getLogger(YConfiguration.class.getName());
    static String prefix = null;

    // keeps track of the configuration path so meaningful error messages can be printed
    // the path is something like filename.key1.subkey2[3]...
    // this is used for the old style when the methods of YConfiguration were called in a static way
    // Nowadays, please use Yconfiguration.getConfig() to make a child config, and then use the .path() to get the
    // similar path.
    static private IdentityHashMap<Object, String> staticConfPaths = new IdentityHashMap<>();

    static final private YConfiguration EMPTY_CONFIG = YConfiguration.wrap(Collections.emptyMap());
    /**
     * The parent configuration
     */
    YConfiguration parent;
    // the key with which this object can be found in its parent
    String parentKey;
    // the root map
    Map<String, Object> root;

    // this is set only for the root Yconfiguration (i.e. without a parent) and indicates where (which file) it has been
    // loaded from
    String rootLocation;

    private YConfiguration(String subsystem) throws IOException, ConfigurationException {
        this(subsystem, resolver.getConfigurationStream("/" + subsystem + ".yaml"), subsystem + ".yaml");
    }

    /**
     * Constructs a new configuration object parsing the input stream
     * 
     * @param is
     *            input stream where the configuration is loaded from
     * @param confpath
     *            configuration path - it is remembered together with the configuration in case of error to indicate
     *            where it is coming from (i.e. which file)
     */
    @SuppressWarnings("unchecked")
    public YConfiguration(String subsystem, InputStream is, String confpath) {
        this.rootLocation = confpath;
        Yaml yaml = new Yaml();
        try {
            Object o = yaml.load(is);
            if (o == null) {
                o = new HashMap<String, Object>(); // config file is empty, not an error
            } else if (!(o instanceof Map<?, ?>)) {
                throw new ConfigurationException(confpath, "top level structure must be a map and not a " + o);
            }
            root = (Map<String, Object>) o;
            staticConfPaths.put(root, confpath);
        } catch (YAMLException e) {
            throw new ConfigurationException(confpath, e.toString(), e);
        }
        configurations.put(subsystem, this);
    }

    /**
     * 
     * @param parent
     * @param parentKey
     * @param root
     */
    public YConfiguration(YConfiguration parent, String parentKey, Map<String, Object> root) {
        this.root = root;
        this.parent = parent;
        this.parentKey = parentKey;
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for client tools and make store or use files from <tt>~/.yamcs</tt>.
     */
    public synchronized static void setupTool() {
        File userConfigDirectory = new File(System.getProperty("user.home"), ".yamcs");
        setupTool(userConfigDirectory);
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for client tools that wish to customize the default config directory.
     * 
     * @param configDirectory
     */
    public synchronized static void setupTool(File configDirectory) {
        if (System.getProperty("java.util.logging.config.file") == null) {
            try {
                LogManager.getLogManager().readConfiguration(resolver.getConfigurationStream("/logging.properties"));
            } catch (Exception e) {
                // do nothing, the default java builtin logging is used
            }
        }

        TimeEncoding.setUp();

        YConfiguration.configDirectory = configDirectory;
        File logDir = new File(configDirectory, "log");
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                System.err.println("Created directory: " + logDir);
            } else {
                System.err.println("Cannot create directory: " + logDir);
            }
        }
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for use in unit and integration tests. It allows resolving configuration files from a
     * specific subdirectory of the classpath.
     *
     * @param configPrefix
     *            the name of the subdirectory where to resolve configuration files. This is resolved from the
     *            classpath.
     */
    public static synchronized void setupTest(String configPrefix) {
        prefix = configPrefix;
        configurations.clear(); // forget any known config (useful in the maven unit tests called in the same VM)

        if (System.getProperty("java.util.logging.config.file") == null) {
            try {
                LogManager.getLogManager().readConfiguration(resolver.getConfigurationStream("/logging.properties"));
            } catch (Exception e) {
                // do nothing, the default java builtin logging is used
            }
        }

        TimeEncoding.setUp();
    }

    /**
     * Loads (if not already loaded) and returns a configuration corresponding to a file &lt;subsystem&gt;.yaml
     *
     * This method does not reload the configuration file if it has changed.
     *
     * @param subsystem
     * @return the loaded configuration
     * @throws ConfigurationException
     *             if the configuration file could not be found or not loaded (e.g. error in yaml formatting)
     */
    public synchronized static YConfiguration getConfiguration(String subsystem) throws ConfigurationException {
        if (subsystem.contains("..") || subsystem.contains("/")) {
            throw new ConfigurationException("Invalid subsystem '" + subsystem + "'");
        }
        YConfiguration c = configurations.get(subsystem);
        if (c == null) {
            try {
                c = new YConfiguration(subsystem);
            } catch (IOException e) {
                throw new ConfigurationException("Cannot load configuration for subsystem " + subsystem + ": " + e);
            }
            configurations.put(subsystem, c);
        }
        return c;
    }

    /**
     * Loads and returns a configuration corresponding to a file &lt;subsystem&gt;.yaml
     *
     * This method reloads the configuration file always.
     *
     * @param subsystem
     * @param reload
     * @return the loaded configuration
     * @throws ConfigurationException
     *             if the configuration file could not be found or not loaded (e.g. error in yaml formatting)
     */
    public synchronized static YConfiguration getConfiguration(String subsystem, boolean reload)
            throws ConfigurationException {
        if (reload) {
            YConfiguration c = configurations.get(subsystem);
            if (c != null) {
                configurations.remove(subsystem);
            }
        }
        return getConfiguration(subsystem);
    }

    public static boolean isDefined(String subsystem) throws ConfigurationException {
        try {
            getConfiguration(subsystem);
            return true;
        } catch (ConfigurationNotFoundException e) {
            return false;
        }
    }

    public static boolean isNull(Map m, String key) {
        if (!m.containsKey(key)) {
            throw new ConfigurationException(staticConfPaths.get(m), "cannot find a mapping for key '" + key + "'");
        }
        Object o = m.get(key);
        return o == null;
    }

    private void checkKey(String key, Class<?> cls) throws ConfigurationException {
        if (!root.containsKey(key)) {
            throw new ConfigurationException(getPath(), "cannot find a mapping for key '" + key + "'");
        }
        Object o = root.get(key);
        if (o == null) {
            throw new ConfigurationException(getPath(), key + " exists but is null");
        }
        if (!cls.isInstance(o)) {
            throw new ConfigurationException(getPath(), key + " is not of the expected type " + cls.getName());
        }
    }

    private static void checkKey(Map m, String key) throws ConfigurationException {
        if (!m.containsKey(key)) {
            throw new ConfigurationException(staticConfPaths.get(m), "cannot find a mapping for key '" + key + "'");
        } else if (m.get(key) == null) {
            throw new ConfigurationException(staticConfPaths.get(m), key + " exists but is null");
        }
    }

    public boolean containsKey(String key) {
        return root.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(String key, String key1) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return false;
        }
        checkKey(key, Map.class);
        Map<String, Object> m = (Map<String, Object>) root.get(key);
        return m.containsKey(key1);
    }

    /**
     * returns the first entry in the config file if it's a map. Otherwise throws an error
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFirstMap() throws ConfigurationException {
        Object o = root.values().iterator().next();
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        } else {
            throw new ConfigurationException(
                    "the first entry in the config is of type " + o.getClass() + " and not Map");
        }
    }

    /**
     * returns the first entry(key) in the config file.
     * 
     * @return
     */
    public String getFirstEntry() throws ConfigurationException {
        return root.keySet().iterator().next();
    }

    public Set<String> getKeys() {
        return root.keySet();
    }

    private static String getUnqualfiedClassName(Object o) {
        String name = o.getClass().getName();
        if (name.lastIndexOf('.') > 0) {
            name = name.substring(name.lastIndexOf('.') + 1); // Map$Entry
        }
        // The $ can be converted to a .
        name = name.replace('$', '.'); // Map.Entry
        return name;
    }

    public Map<String, Object> getRoot() {
        return root;
    }

    /**
     * If the key is pointing to a map, creates and returns a configuration object out of that map
     * 
     * <p>
     * The returned object will have its parent set to this object
     * 
     * @param key
     * @return
     */
    public YConfiguration getConfig(String key) {
        Map<String, Object> m = getMap(key);
        return new YConfiguration(this, key, m);
    }

    @SuppressWarnings("unchecked")
    static public Map<String, Object> getMap(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Map) {
            Map<String, Object> m1 = (Map) o;
            if (staticConfPaths.containsKey(m1)) {
                staticConfPaths.put(m1, staticConfPaths.get(m) + "->" + key);
            }
            return m1;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + o.getClass().getCanonicalName() + " and not Map");
        }
    }

    /**
     * 
     * Consider using {@link #getConfig} to get a child config instead of accessing the map directly
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key) throws ConfigurationException {
        checkKey(key, Map.class);
        return (Map<K, V>) root.get(key);
    }

    public Map<String, Object> getSubMap(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getMap(m, key1);
    }

    /**
     * Returns m.get(key) if it exists and is of type string, otherwise throws an exception
     * 
     * @param m
     * @param key
     * @return
     * @throws ConfigurationException
     */
    static public String getString(Map m, String key) throws ConfigurationException {
        checkKey(m, key);

        Object o = m.get(key);
        if (o instanceof String) {
            return (String) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not String");
        }
    }

    static public String getString(Map m, String key, String defaultValue) throws ConfigurationException {
        if (m.containsKey(key)) {
            return getString(m, key);
        } else {
            return defaultValue;
        }
    }

    public String getString(String key) throws ConfigurationException {
        checkKey(key, String.class);
        return (String) root.get(key);
    }

    public String getString(String key, String defaultValue) throws ConfigurationException {
        return getString(root, key, defaultValue);
    }

    /*
     * The key has to point to a map that contains the subkey that points to a string
     */
    public String getSubString(String key, String subkey) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getString(m, subkey);
    }

    /*
     * The key has to point to a list
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) throws ConfigurationException {
        checkKey(key, List.class);
        return (List<T>) root.get(key);
    }

    @SuppressWarnings("unchecked")
    public List<YConfiguration> getConfigList(String key) throws ConfigurationException {
        checkKey(root, key);
        List<YConfiguration> r = new ArrayList<>();
        Object o = root.get(key);
        if (o instanceof List) {
            List l = (List) o;
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (o1 instanceof Map) {
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map) o1));
                } else {
                    throw new ConfigurationException(this, "One element of the list is not a map: " + o1);
                }
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not List");
        }
        return r;
    }

    public double getDouble(String key) throws ConfigurationException {
        checkKey(key, Number.class);
        return ((Number) root.get(key)).doubleValue();
    }

    public double getDouble(String key, double defaultValue) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return defaultValue;
        }
        return getDouble(key);
    }

    /**
     * This is the same like the method above but will create a {class: "string"} for strings rather than throwing an
     * exception. It is to be used when loading service list which can be specified just by the class name.
     * 
     * @param key
     * @return
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    public List<YConfiguration> getServiceConfigList(String key) throws ConfigurationException {
        checkKey(root, key);
        List<YConfiguration> r = new ArrayList<>();
        Object o = root.get(key);
        if (o instanceof List) {
            List l = (List) o;
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (o1 instanceof Map) {
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map) o1));
                } else if (o1 instanceof String) {
                    Map<String, Object> m1 = new HashMap<>();
                    m1.put("class", o1);
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map) m1));
                } else {
                    throw new ConfigurationException(this, "One element of the list is not a map: " + o1);
                }
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not List");
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getSubList(String key, String key1) throws ConfigurationException {
        checkKey(key, Map.class);
        Map<String, Object> m = (Map<String, Object>) root.get(key);
        return getList(m, key1);
    }

    /**
     * Returns m.get(key) if it exists and is of type boolean, if m.get(key) exists and is not boolean, throw an
     * exception. if m.get(key) does not exist, return the default value.
     * 
     * @param m
     * @param key
     * @param defaultValue
     *            - the default value to return if m.get(key) does not exist.
     * @return the boolean config value
     * @throws ConfigurationException
     */
    static public boolean getBoolean(Map<String, Object> m, String key, boolean defaultValue)
            throws ConfigurationException {
        Object o = m.get(key);
        if (o != null) {
            if (o instanceof Boolean) {
                return (Boolean) o;
            } else {
                throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                        + getUnqualfiedClassName(o) + " and not Boolean (use true or false without quotes)");
            }
        } else {
            return defaultValue;
        }
    }

    static public boolean getBoolean(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Boolean) {
            return (Boolean) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualfiedClassName(o) + " and not Boolean (use true or false without quotes)");
        }
    }

    public boolean getBoolean(String key) throws ConfigurationException {
        checkKey(key, Boolean.class);
        return (Boolean) root.get(key);
    }

    public boolean getBoolean(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getBoolean(m, key1);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(root, key, defaultValue);
    }

    public int getInt(String key) throws ConfigurationException {
        checkKey(key, Integer.class);
        return (Integer) root.get(key);
    }

    public int getInt(String key, int defaultValue) throws ConfigurationException {
        if (root.containsKey(key)) {
            return getInt(key);
        } else {
            return defaultValue;
        }
    }

    public int getInt(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getInt(m, key1);
    }

    public int getInt(String key, String key1, int defaultValue) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return defaultValue;
        }

        Map<String, Object> m = getMap(key);

        return getInt(m, key1, defaultValue);
    }

    static public int getInt(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not Integer");
        }
    }

    /**
     * return the m.get(key) as an int if it's present or v if it is not.
     *
     * If the key is present but the value is not an integer, a ConfigurationException is thrown.
     * 
     * @param m
     * @param key
     * @param defaultValue
     * @return the value from the map or the passed value if the map does not contain the key
     * @throws ConfigurationException
     *             if the key is present but it's not an int
     */
    static public int getInt(Map<String, Object> m, String key, int defaultValue) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return defaultValue;
        }
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not Integer");
        }
    }

    public long getLong(String key) {
        return getLong(root, key);
    }

    public long getLong(String key, long defaultValue) {
        return getLong(root, key, defaultValue);
    }

    static public long getLong(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof Long) {
            return (Long) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualfiedClassName(o) + " and not Integer or Long");
        }
    }

    public byte[] getBinary(String key) {
        return getBinary(root, key);
    }

    public byte[] getBinary(String key, byte[] defaultValue) {
        if (root.containsKey(key)) {
            return getBinary(root, key);
        } else {
            return defaultValue;

        }
    }

    static public byte[] getBinary(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof byte[]) {
            return (byte[]) o;
        } else if (o instanceof String) {
            String s = (String) o;
            try {
                return StringConverter.hexStringToArray((String) o);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("'" + s + "' is not an hexadecimal string");
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualfiedClassName(o) + " and not binary or hexadecimal string");
        }
    }

    /**
     * return the m.get(key) as an long if it's present or v if it is not.
     *
     * @param m
     * @param key
     * @param v
     * @return the value from the map or the passed value if the map does not contain the key
     * @throws ConfigurationException
     *             if the key is present but it's not an long
     */
    static public long getLong(Map<String, Object> m, String key, long v) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return v;
        }
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof Long) {
            return (Long) o;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualfiedClassName(o) + " and not Integer or Long");
        }
    }

    static public double getDouble(Map<String, Object> m, String key, double v) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return v;
        }
        Object o = m.get(key);
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualfiedClassName(o) + " and not Integer or Long");
        }
    }

    public boolean isList(String key) {
        return isList(root, key);
    }

    public static boolean isList(Map m, String key) {
        checkKey(m, key);
        Object o = m.get(key);
        return (o instanceof List);
    }

    public static void setResolver(YConfigurationResolver resolver) {
        YConfiguration.resolver = resolver;
    }

    public static YConfigurationResolver getResolver() {
        return YConfiguration.resolver;
    }

    /**
     * Default config file resolver. Looks for configuration files in the classpath and in the user config directory
     * (~/.yamcs/).
     */
    public static class DefaultConfigurationResolver implements YConfigurationResolver {
        @Override
        public InputStream getConfigurationStream(String name) throws ConfigurationException {
            InputStream is;
            if (prefix != null) {
                if ((is = YConfiguration.class.getResourceAsStream("/" + prefix + name)) != null) {
                    log.debug("Reading {}", new File(YConfiguration.class.getResource("/" + prefix + name).getFile())
                            .getAbsolutePath());
                    return is;
                }
            }

            // see if the users has an own version of the file
            if (configDirectory != null) {
                File f = new File(configDirectory, name);
                if (f.exists()) {
                    try {
                        is = new FileInputStream(f);
                        log.debug("Reading {}", f.getAbsolutePath());
                        return is;
                    } catch (FileNotFoundException e) {
                        throw new ConfigurationException("Cannot read file " + f, e);
                    }
                }
            }

            is = YConfiguration.class.getResourceAsStream(name);
            if (is == null) {
                throw new ConfigurationNotFoundException("Cannot find resource " + name);
            }
            log.debug("Reading {}", new File(YConfiguration.class.getResource(name).getFile()).getAbsolutePath());
            return is;
        }
    }

    /**
     * Introduced to be able to detect when a configuration file was not specified (as opposed to when there's a
     * validation error inside). The current default behaviour of Yamcs is to throw an error when
     * getConfiguration(String subystem) is called and the resource does not exist.
     */
    public static class ConfigurationNotFoundException extends ConfigurationException {
        private static final long serialVersionUID = 1L;

        public ConfigurationNotFoundException(String message) {
            super(message);
        }

        public ConfigurationNotFoundException(String message, Throwable t) {
            super(message, t);
        }
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass) {
        return getEnum(root, key, enumClass);
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        if (root.containsKey(key)) {
            return getEnum(root, key, enumClass);
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns a value of an enumeration that matches ignoring case the string obtained from the config with the given
     * key. Throws an Configurationexception if the key does not exist in config or if it does not map to a valid
     * enumeration value
     * 
     * @param config
     * @param key
     * @param enumClass
     * @return
     */
    public static <T extends Enum<T>> T getEnum(Map<String, Object> config, String key, Class<T> enumClass) {
        String sk = getString(config, key);

        T[] values = enumClass.getEnumConstants();
        for (T v : values) {
            if (v.toString().equalsIgnoreCase(sk)) {
                return v;
            }
        }
        throw new ConfigurationException("Invalid value '" + sk + "'. Valid values are: " + Arrays.toString(values));
    }

    /**
     * 
     * @param key
     * @return root.get(key)
     */
    public Object get(String key) {
        return root.get(key);
    }

    /**
     * Create a new configuration wrapping around a map The resulting config will have no parent
     * 
     * @param m
     * @return
     */
    public static YConfiguration wrap(Map<String, Object> m) {
        return new YConfiguration(null, null, m);
    }

    public static YConfiguration emptyConfig() {
        return EMPTY_CONFIG;
    }

    public Map<String, Object> toMap() {
        return getRoot();
    }

    public String getPath() {
        if (parent == null) {
            return rootLocation;
        }

        StringBuilder sb = new StringBuilder();
        buildPath(this, sb);
        return sb.toString();
    }

    private static void buildPath(YConfiguration c, StringBuilder sb) {
        if (c.parent != null) {
            buildPath(c.parent, sb);
            if (c.parent.parent != null) {
                sb.append(".");
            }
            sb.append(c.parentKey);
        } else {
            sb.append(c.rootLocation).append(": ");
        }
    }

    @SuppressWarnings("unchecked")
    static public <T> List<T> getList(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof List) {
            List l = (List) o;
            String parentPath = staticConfPaths.get(m);
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (!staticConfPaths.containsKey(o1)) {
                    staticConfPaths.put(o1, parentPath + "->" + key + "[" + i + "]");
                }
            }
            return l;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualfiedClassName(o) + " and not List");
        }
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
