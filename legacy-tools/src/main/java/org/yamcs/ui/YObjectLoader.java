package org.yamcs.ui;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class YObjectLoader<T> {

    private static Logger log = LoggerFactory.getLogger(YObjectLoader.class);

    /**
     * Loads classes defined in the yamcs server or client configuration properties
     * 
     * @param className
     * @param args
     * @return an object of the given class instantiated with the given parameters
     * @throws ConfigurationException
     * @throws IOException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static public <T> T loadObject(String className, Object... args) throws ConfigurationException, IOException {
        try {
            Class ic = Class.forName(className);
            Constructor<T> constructor = null;
            Constructor[] constructors = ic.getConstructors();
            for (Constructor c : constructors) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length != args.length) {
                    continue;
                }
                boolean ok = true;
                for (int i = 0; i < params.length; i++) {
                    if ((args[i] != null) && !params[i].isAssignableFrom(args[i].getClass())) {
                        if (args[i] instanceof YConfiguration) {
                            YConfiguration yc = (YConfiguration) args[i];
                            if (params[i].isAssignableFrom(yc.getRoot().getClass())) {
                                log.warn(
                                        "The class {} uses  a Map<String, Object> in the constructor; please replace it with the YConfiguration",
                                        className);
                                args[i] = yc.getRoot();
                                continue;
                            }
                        }
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    constructor = c;
                    break;
                }
            }
            if (constructor == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("Cannot find a constructor for class '" + className + "' and arguments (");
                boolean first = true;
                for (Object o : args) {
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    if (o == null) {
                        sb.append("java.lang.Object");
                    } else {
                        sb.append(o.getClass().getName());
                    }
                }
                sb.append(")");
                throw new ConfigurationException(sb.toString());
            } else {

                return constructor.newInstance(args);
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof ConfigurationException) {
                throw (ConfigurationException) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof ExceptionInInitializerError) {
                throw new ConfigurationException(
                        "Cannot instantiate object from class " + className + ": " + t.getCause(), t.getCause());
            } else {
                throw new ConfigurationException("Cannot instantiate object from class " + className + ": " + t, t);
            }
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Cannot instantiate object from class " + className + ": " + e, e);
        }
    }

    /**
     * loads an object defined like this: class: org.yamcs.... args: key1: value1 key2: value2
     * 
     * "args" can also be called "config" or can be missing. The value of args can also be a list or a scalar type. args
     * can also be called config or spec.
     * 
     * If args is present, then a constructor with the given type is invoked otherwise the constructor without any
     * argument is invoked.
     * 
     * 
     * @param conf
     * @return a new object
     * @throws IOException
     * @throws ConfigurationException
     */
    static public <T> T loadObject(Map<String, Object> conf) throws ConfigurationException, IOException {
        String className = YConfiguration.getString(conf, "class");
        Object args = getArgs(conf);

        if (args != null) {
            return loadObject(className, args);
        } else {
            return loadObject(className);
        }
    }

    /**
     * same as the method above but loads a constructor with the firstArg as the first argument
     * 
     * @param conf
     * @param firstArg
     * @return a newly created object
     * @throws ConfigurationException
     * @throws IOException
     */
    static public <T> T loadObject(Map<String, Object> conf, Object firstArg)
            throws ConfigurationException, IOException {
        String className = YConfiguration.getString(conf, "class");
        Object args = getArgs(conf);

        if (args != null) {
            return loadObject(className, firstArg, args);
        } else {
            return loadObject(className, firstArg);
        }
    }

    /**
     * same as the method above but loads a constructor with firstArg and secondArg as the first two arguments
     * 
     * @param conf
     * @param firstArg
     * @param secondArg
     * @return a newly created object
     * @throws ConfigurationException
     * @throws IOException
     */
    static public <T> T loadObject(Map<String, Object> conf, Object firstArg, Object secondArg)
            throws ConfigurationException, IOException {
        String className = YConfiguration.getString(conf, "class");
        Object args = getArgs(conf);

        if (args != null) {
            return loadObject(className, firstArg, secondArg, args);
        } else {
            return loadObject(className, firstArg, secondArg);
        }
    }

    static private Object getArgs(Map<String, Object> conf) {
        if (conf.containsKey("config")) {
            return conf.get("config");
        } else if (conf.containsKey("args")) {
            return conf.get("args");
        } else if (conf.containsKey("spec")) {
            return conf.get("spec");
        } else {
            return null;
        }
    }
}
