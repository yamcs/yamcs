//this file contains some "static" loading, parsing and other util functions

var USS = {
   computationCount:0,
   widgetCount: 0,
   
   opsNamespace: 'MDB:OPS Name',
   //dynamic properties
   dp_VALUE: 'VALUE',
   dp_X: 'X',
   dp_Y: 'Y',
   dp_FILL_COLOR: 'FILL_COLOR'
}; //USS namespace

USS.parseDataBinding = function(e) {
    var db=new Object();
    db.dynamicProperty=$(e).children('DynamicProperty').text();
    var ds=$(e).children('DataSource')[0];
    if(ds.hasAttribute('reference')) {
        ds=USS.getReferencedElement(ds);
    }
    db.type=ds.getAttribute('class');
    if(db.type=='ExternalDataSource') {
        $('Names entry', ds).each(function(idx, val) {
            var n=$('string:nth-child(1)', val).text(); //one of 'Opsname', 'Pathname' or 'SID'
            db[n]=$('string:nth-child(2)', val).text();
        });
        if(db.Opsname!==undefined) {
            db.parameterName=db.Opsname.trim();
            db.parameterNamespace = USS.opsNamespace.trim();
         } else {
            console.log("External Data source without Opsname", ds);
         }
         db.usingRaw=($(ds).children('UsingRaw').text().toLowerCase()==='true');
    } else if (db.type=='Computation') {
       var pname="__uss_computation"+USS.computationCount;
       USS.computationCount++;
       var c=new Object();
       c.expression=$(ds).children('Expression').text();
       c.args=[];
       c.parameterName=pname;

       $('Arguments ExternalDataSource', e).each(function(idx, val) {
            var arg=new Object();
            $('Names entry', val).each(function(idx, val1) {
                var n=$('string:nth-child(1)', val1).text(); //one of 'Opsname', 'Pathname' or 'SID'
                arg[n]=$('string:nth-child(2)', val1).text();
            });
            c.args.push(arg);
        });
       var names=$(ds).children('Names');
       $('entry', names).each(function(idx, val) {
            var n=$('string:nth-child(1)', val).text(); //DEFAULT
            db[n]=$('string:nth-child(2)', val).text();
        });
       db.expression=c.expression;
       db.args=c.args;
       db.parameterName=pname;
    }
    return db;
};

/*
/*
* Writes text in the bounding box opts:x,y,width,height
* TODO: only works for left to right horizontal text
*/
USS.writeText = function(svg, parent, opts, textStyle, text) {
    var settings={id: opts.id};
    $.extend(settings, USS.parseTextStyle(textStyle));
    var horizAlignment=$(textStyle).children('HorizontalAlignment').text().toLowerCase();
    var vertAlignment=$(textStyle).children('VerticalAlignment').text().toLowerCase();
    var x;
    if(horizAlignment == "center") {
        x=opts.x+opts.width/2;
        settings.textAnchor='middle';
    } else if(horizAlignment == "left") {
        x=opts.x;
        settings.textAnchor='start';
    } else if(horizAlignment == "right") {
        x=opts.x+opts.width;
        settings.textAnchor='end';
    }

    text = text.split(" ").join("\u00a0"); // Preserve whitespace
    var t=svg.text(parent, x, opts.y, text, settings);
    var bbox=t.getBBox();
    //shift to have the bbox correspond to x,y,width,height
    if(vertAlignment == "center") {
        t.setAttribute('dy', opts.y - bbox.y + (opts.height - bbox.height)/2);
    } else if(vertAlignment == "top") {
        t.setAttribute('dy', opts.y-bbox.y);
    } else if(vertAlignment == "bottom") {
        t.setAttribute('dy', opts.y - bbox.y + opts.height - bbox.height);
    }
    return t;
};

USS.parseFillStyle = function(e) {
    var fs=new Object();
    var obj=$(e).children('FillStyle');
    fs.fill=USS.parseColor($(obj).children('Color')[0]);
    var pattern=$(obj).children('Pattern').text().toLowerCase();
    if(pattern === 'solid') {
        fs.fillOpacity=1;
    } else {
        fs.fillOpacity=0;
    }
   return fs
};

USS.parseDrawStyle = function(e) {
    var ds=new Object();
    var obj=$(e).children('DrawStyle');
    var pattern=$(obj).children('Pattern').text().toLowerCase();
    if(pattern === 'solid') {
        ds.strokeOpacity=1;
    } else {
        ds.strokeOpacity=0;
    }
    ds.stroke=USS.parseColor($(obj).children('Color'));
    ds.strokeWidth=$(obj).children('Width').text();
    return ds;
};

USS.parseTextStyle=function(e) {
    var ts=new Object();
    ts.fontSize=$(e).children('Fontsize').text()+'px';
    ts.fontFamily=$(e).children('Fontname').text();
    if(ts.fontFamily=='Lucida Sans Typewriter') {
       ts.fontFamily='Lucida Sans Typewriter, monospace';
    }
    var bold= ($("IsBold:first", e).text().toLowerCase() === 'true');
    if(bold)  ts.fontWeight="bold";
    var italic= ($("IsItalic:first", e).text().toLowerCase() === 'true');
    if(italic)  ts.fontStyle="italic";
    var underline= ($("IsUnderlined:first", e).text().toLowerCase() === 'true');
    if(underline) ts.textDecoration="underline";
    ts.fill=USS.parseColor($(e).children('Color')[0]);
    return ts;
};


USS.parseColor =function(e, defaultColor) {
    if(!e) return defaultColor;
    var $e = $(e);
    var red=$e.children('red').text();
    var green=$e.children('green').text();
    var blue=$e.children('blue').text();
    var alpha=$e.children('alpha').text();
    return "rgba("+red+","+green+","+blue+","+alpha+")";
};

USS.getReferencedElement = function(e) {
    var tokens=e.getAttribute('reference').split('/');
    
    for (var i in tokens) {
        var token=tokens[i];
        if (token == "..") {
            e = e.parentNode;
        } else {
            var idx = 0;
            var k = token.indexOf('[');
            var nodeName=token;
            if (k != -1) {
                var idxStr = token.substring(k + 1, token.indexOf(']', k));
                idx = parseInt(idxStr)-1;
                nodeName = token.substring(0, k);
            }
            e=$(e).children(nodeName)[idx];
        }
    }
   return e; 
};

//creates a definition section in the SVG and adds the markers that will be used for polylines arrows
// TODO: It is broken currently because the markers will show all in black, instead of the color of the line
USS.addArrowMarkers = function(svg) {
        var defs = svg.defs();

        var settings = {overflow: 'visible', fill:'currentColor', stroke: 'none'};
        var arrowMarkerStart = svg.marker(defs, 'uss-arrowStart', 0, 0, 20, 20, 'auto', settings);

        var path = svg.createPath();
        svg.path(arrowMarkerStart, path.move(0, -15).line(-20, 0).line(0, 15),
            {fillRule: 'evenodd', fillOpacity: '1.0', transform: 'scale(0.2, 0.2) translate(20, 0)'});

        var arrowMarkerEnd = svg.marker(defs, 'uss-arrowEnd', 0, 0, 20, 20, 'auto', settings);

        path = path.reset();
        svg.path(arrowMarkerEnd, path.move(0, -15).line(-20, 0).line(0, 15),
            {fillRule: 'evenodd', fillOpacity: '1.0', transform: 'scale(0.2, 0.2) rotate(180) translate(20, 0)'});
};

USS.engValueToString = function(engValue, decimals) {
   switch(engValue.type) {
       case 0:
         return engValue.floatValue.toFixed(decimals);
       case 1:
         return engValue.doubleValue.toFixed(decimals);
    }
   for(var idx in engValue) {
      if(idx!='type') return engValue[idx];
   }
};

USS.getParameterValue = function(param, usingRaw) {
    if(usingRaw) {
        var rv=param.rawValue;
        for(var idx in rv) {
            if(idx!='type') return rv[idx];
        }
    } else {
        var ev=param.engValue;
        if(ev === undefined) {
            console.log('got parameter without engValue: ', param);
            return null;
        }
        switch(ev.type) {
            case 'FLOAT':
                return ev.floatValue;
            case 'DOUBLE':
                return ev.doubleValue;
            case 'BINARY':
                return window.atob(ev.binaryValue);
        }
        for(var idx in ev) {
            if(idx!='type') return ev[idx];
        }
    }
};

//used from the NavigationButton
USS.openDisplay = function(displayBaseName) {
    if(typeof(showDisplay) === 'function') {
        showDisplay(displayBaseName+".uss");
    } else {
        alert("This button only works when the display is running inside the full USS app");
    }
};


//get the parameter id for the parameter shown in the widget for plotting or info
USS.getParameterFromWidget = function(widget) {
   for(var i in widget.dataBindings) {
       var p = widget.dataBindings[i];
       if(p.dynamicProperty==USS.dp_VALUE) {
	    return {name: p.parameterName, namespace: p.parameterNamespace}
       }
   }
};
