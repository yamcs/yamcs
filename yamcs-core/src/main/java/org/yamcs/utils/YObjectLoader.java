package org.yamcs.utils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.yamcs.ConfigurationException;

public class YObjectLoader<T> {

    /**
     * Loads classes defined in the yamcs server or clientconfiguration properties
     * @param className
     * @param args
     * @return
     * @throws ConfigurationException
     * @throws IOException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public T loadObject(String className, Object... args) throws ConfigurationException, IOException {
        try {
            Class ic=Class.forName(className);
            Constructor<T> constructor=null;
            Constructor[] constructors = ic.getConstructors();
            for(Constructor c:constructors) {
                Class<?>[] params = c.getParameterTypes();
                if(params.length!=args.length) continue;
                boolean ok=true;
                for(int i=0;i<params.length;i++) {
                    if(!params[i].isAssignableFrom(args[i].getClass())) {
                        ok=false;
                        break;
                    }
                }
                if(ok) {
                    constructor=c;
                    break;
                }
            }
            if(constructor==null){
                StringBuilder sb=new StringBuilder();
                sb.append("Cannot find a constructor for class '"+className+"' and arguments (");
                boolean first=true;
                for(Object o: args) {
                    sb.append(o.getClass().getName());
                    if(!first)sb.append(", ");
                    else first = false;
                }
                sb.append(")");
                throw new ConfigurationException(sb.toString());
            } else {
                return constructor.newInstance(args);
            }
        } catch (InvocationTargetException e) {
            Throwable t=e.getCause();
            if(t instanceof ConfigurationException) {
                throw (ConfigurationException)t;
            } else if (t instanceof IOException) {
                throw (IOException)t;
            } else if (t instanceof ExceptionInInitializerError) {
            	throw new ConfigurationException("Cannot instantiate object from class " +className+": "+t.getCause(), t.getCause());
            } else {
                throw new ConfigurationException("Cannot instantiate object from class " +className+": "+t, t);
            }
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Cannot instantiate object from class "+className+": "+e, e);
        }
    }
}
