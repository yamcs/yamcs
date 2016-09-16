package org.yamcs.utils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YamcsServer;

public class YObjectLoader<T> {
    static Logger log=LoggerFactory.getLogger(YamcsServer.class);
    /**
     * Loads classes defined in the yamcs server or client configuration properties
     * @param className
     * @param args
     * @return an object of the given class instantiated with the given parameters
     * @throws ConfigurationException
     * @throws IOException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public T loadObject(String className, Object... args) throws ConfigurationException, IOException {
        try {
            Class ic = Class.forName(className);
            Constructor<T> constructor = null;
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
                    if(!first)sb.append(", ");
                    else first = false;
                    sb.append(o.getClass().getName());
                }
                sb.append(")");
                throw new ConfigurationException(sb.toString());
            } else {
                checkDeprecated(ic);
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
    
    @SuppressWarnings({"rawtypes" })
    private void checkDeprecated(Class objclass) {
        checkAndPrintDeprecatedWarning("The class "+objclass.getName()+" is deprecated", objclass);
        
        Class c = objclass.getSuperclass();
        while(c!=null) {
            checkAndPrintDeprecatedWarning("The class "+c.getName()+" extended by "+objclass.getName()+" is deprecated", c);
            c = c.getSuperclass();
        }
        for(Class i: objclass.getInterfaces()) {
            checkAndPrintDeprecatedWarning("The class "+objclass.getName()+" implements interface "+i.getName()+" which is deprecated", i);
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void checkAndPrintDeprecatedWarning(String prefix, Class objclass) {
        DeprecationInfo di = (DeprecationInfo) objclass.getAnnotation(DeprecationInfo.class);
        if(di!=null) {
            log.warn(prefix+": "+ di.info());
        } else {
            Annotation a  = objclass.getAnnotation(Deprecated.class);
            if(a!=null) {
                log.warn(prefix+". Please check the javadoc for alternatives.");
            }
        }
        
    }
    
}
