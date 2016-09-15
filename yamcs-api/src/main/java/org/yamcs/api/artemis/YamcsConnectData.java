package org.yamcs.api.artemis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.yamcs.api.ConnectionParameters;


public class YamcsConnectData extends ConnectionParameters implements Cloneable {
    public boolean ssl=false;
    public String instance = null;
    public String username = null;
    public String password = null;

    public YamcsConnectData() {
        host="localhost";
        port=5445;
        username=null;
        password=null;
    }

    public void load() {
        try {
            Properties p=new Properties();
            p.load(new FileInputStream(System.getProperty("user.home")+"/.yamcs/YamcsConnectProperties"));
            host=p.getProperty("host");
            try {port=Integer.parseInt(p.getProperty("port"));} catch (NumberFormatException e){};
            ssl=Boolean.valueOf(p.getProperty("ssl"));
            instance=p.getProperty("instance");
            username=p.getProperty("username");
        } catch (IOException e) {}
    }

    public void save() {
        Properties p=new Properties();
        p.setProperty("host",host);
        p.setProperty("port",Integer.toString(port));
        p.setProperty("ssl",Boolean.toString(ssl));
        if(instance!=null) p.setProperty("instance", instance);
        if(username!=null) p.setProperty("username", username);
        try {
            (new File(System.getProperty("user.home")+"/.yamcs")).mkdirs();
            p.store(new FileOutputStream(System.getProperty("user.home")+"/.yamcs/YamcsConnectProperties"),
                    "Yamcs/Cis connect dialog properties cache");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public YamcsConnectData clone() {
        try {
            return (YamcsConnectData)super.clone();
        } catch (CloneNotSupportedException e) {e.printStackTrace();}//this can't happen
        return this;
    }

    public String getInstance() {
        return instance;
    }

    /**
     * uri is protocol://[[username]:[password]@][host[:port]]/[instance]
     * @param uri
     * @return
     * @throws URISyntaxException
     */
    public static YamcsConnectData parse(String uri) throws  URISyntaxException {
        YamcsConnectData ycd=new YamcsConnectData();
        URI u = new URI(uri);
        if(!"yamcs".equals(u.getScheme()) && !"yamcss".equals(u.getScheme())) {
            throw new URISyntaxException(uri, "only yamcs or yamcss scheme allowed");
        }
        if("yamcss".equals(u.getScheme())) {
            ycd.ssl=true;
        }
        if(u.getPort()!=-1) ycd.port=u.getPort();
        ycd.host=u.getHost();

        if( u.getUserInfo() != null ) {
            String[] ui = u.getRawUserInfo().split(":");
            ycd.username = ui[0];
            if( ui.length > 1 ) {
                ycd.password = ui[1];
            }
        }

        String[] pc=u.getPath().split("/");
        if(pc.length>3) throw new URISyntaxException(uri, "Can only support instance/address paths");
        if(pc.length>1)	ycd.instance=pc[1];

        return ycd;
    }


    @Override
    public String getUrl() {
        StringBuilder sb=new StringBuilder();
        sb.append("yamcs://");
        // Note we don't output the password
        if(username!=null)sb.append(username).append(":@");
        sb.append(host).append(":").append(port).append("/");
        if(instance!=null)sb.append(instance);
        return sb.toString();
    }
}
