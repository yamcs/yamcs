importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
//from org.csstudio.opibuilder.scriptUtil import PVUtil;

    function compute( lat1,  long1,  lat2,  long2,  h1,  h2) {
         var a,esqu,x1,y1,z1,x2,y2,z2;
         //double lat1,long1,h1,lat2,long2,h2;
         var nphi1,nphi2;
         var Range, Elev, Azim;
         var dx,dy,dz;
        a=6378137;
         esqu=0.00669438;
//         lat1=53.3*Math.PI/180;
//         lat2=50*Math.PI/180;
//         long1=60.4*Math.PI/180;
//         long2=60.6*Math.PI/180;
//         h1=5;
//         h2=25000;
         nphi1=a/(Math.sqrt(1-(esqu*Math.sin(lat1)*Math.sin(lat1))));
         nphi2=a/(Math.sqrt(1-esqu*(Math.sin(lat2)*(Math.sin(lat2)))));

         x1=(nphi1+h1)*Math.cos(lat1)*Math.cos(long1);
         y1=(nphi1+h1)*Math.cos(lat1)*Math.sin(long1);
         z1=(nphi1*(1-esqu)+h1)*Math.sin(lat1);

         x2=(nphi2+h2)*Math.cos(lat2)*Math.cos(long2);
         y2=(nphi2+h2)*Math.cos(lat2)*Math.sin(long2);
         z2=(nphi2*(1-esqu)+h2)*Math.sin(lat2);

         dx=x1-x2;
         dy=y1-y2;
         dz=z1-z2;

         Range = Math.sqrt(dx*dx + dy*dy + dz*dz);
         Elev= Math.acos(dz/Range)*180/Math.PI;
         Azim= Math.atan(dy/dx)*180/Math.PI;
         return Range;
    }

var lat1 = 53.3*Math.PI/180;
var lon1 = 60.4*Math.PI/180;
var alt1 = 5;

var lat2 = PVUtil.getDouble(pvs[0])*Math.PI/180;
var lon2 = PVUtil.getDouble(pvs[1])*Math.PI/180;
var alt2 = PVUtil.getDouble(pvs[2]);

widget.setPropertyValue("text", compute(lat1, lon1, lat2, lon2, alt1, alt2).toFixed(0));
