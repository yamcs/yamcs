/*contains the following objects
* Display
* and basic widgets
*    Label
*    Field
*    Polyline
*    Rectangle
*    Symbol
*    ExternalImage
*    NavigationButton
*************/

(function () {


USS.Display = function(div) {
    this.widgets = {};
    this.parameters = {};
    this.div = div;
    this.bgcolor = '#D4D4D4';
};


USS.Display.prototype = {
    constructor: USS.Display,
    parseAndDraw: function (xmlDoc) {
        var display=$("Display:first", xmlDoc);
        var width = this.width = parseInt(display.children('Width').text());
        var height = this.height = parseInt(display.children('Height').text());
 
        var svg = this.svg = $(this.div).svg('get');

        svg.configure({
            height: height,
            width: width,
            class: 'canvas',
            'xmlns': 'http://www.w3.org/2000/svg',
            'xmlns:xlink': 'http://www.w3.org/1999/xlink'
        });
        
        USS.addArrowMarkers(svg);
        
        //draw background
        this.bgcolor=USS.parseColor($(display).children("BackgroundColor")[0], '#D4D4D4');
        
        svg.rect(0, 0, width, height, {fill: this.bgcolor});
        this.drawElements(svg, null, display.children("Elements").children());
    },
        

    drawElements: function(svg, parent, elements) {
        //sort element such that they are drawn in order of their Depth
        // TODO: those that have the same Depth have to still be sorted according to some TBD behaviour
        elements=Array.prototype.slice.call(elements,0);
        for(var i=0;i<elements.length;i++) {
            var e=elements[i];
            e.widgetType=e.nodeName;
            if(e.hasAttribute('reference')) {
                elements[i] = USS.getReferencedElement(e);
                elements[i].widgetType = e.widgetType;
            }
        }
        elements.sort(function(a,b) {
            var da=parseInt($(a).children('Depth').text());
            var db=parseInt($(b).children('Depth').text());
            var bname=$(b).children('Name').text();
            return da-db;
        });
        for(var i=0;i<elements.length;i++) {
            this.drawWidget(svg, parent, elements[i]);
        }
    },

    drawWidget: function(svg, parent, e) {
        var opts=this.parseStandardOptions(e);
        var widgetType=e.widgetType;
         
        if(widgetType=="Compound") { 
            //this corresponds to the group feature of USS. We make a SVG group.
            var g=svg.group(parent, opts.id);
            this.drawElements(svg, g, $(e).children('Elements').children());
        } else if(typeof (USS[widgetType]) === 'function') {
            var w = new USS[widgetType]();
            //make the standard properties part of the object
            w.id = "w" + USS.widgetCount;
            USS.widgetCount+=1;
            w.x = opts.x; w.y = opts.y; 
            w.width = opts.width; w.height = opts.height;
            w.dataBindings = opts.dataBindings;
            w.svg = svg;

            w.parseAndDraw(svg, parent, e);
            var len=opts.dataBindings.length;

            if(len>0) { 
                this.widgets[w.id]=w; //we only remember those widgets that have dynamic properties
                for(var i=0; i<len; i++) {
                    var db = opts.dataBindings[i];
                    var para = this.parameters[db.parameterName];
                    if (para === undefined) {
                        para = {name: db.parameterName, namespace:db.parameterNamespace, type: db.type, bindings:[]};
                        if(para.type == 'Computation') {
                           para.expression = db.expression;
                           para.args=db.args;
                        }
                        this.parameters[db.parameterName] = para;
                    }
                    var binding = {
                        dynamicProperty: db.dynamicProperty,
                        widget: w,
                        updateWidget: function(para) {
                            console.log('heh2');

                            switch (this.dynamicProperty) {
                                case USS.dp_VALUE:
                                    if (this.widget.updateValue === undefined) {
                                        //console.log('no updateValue for ', widget);
                                        return;
                                    }
                                    this.widget.updateValue(para, this.usingRaw);
                                    break;
                                case USS.dp_X:
                                    this.widget.updatePosition(para, 'x', this.usingRaw);
                                    break;
                                case USS.dp_Y:
                                    this.widget.updatePosition(para, 'y', this.usingRaw);
                                    break;
                                case USS.dp_FILL_COLOR:
                                    this.widget.updateFillColor(para, this.usingRaw);
                                    break;
                                default:
                                    console.log('TODO update dynamic property: ', this.dynamicProperty);
                            }
                        }
                    };

                    if(db.usingRaw !== undefined) {
                        binding.usingRaw=db.usingRaw;
                    }

                    para.bindings.push(binding);
                }
           }
        } else {
            console.log("TODO: widgetType: "+widgetType);
        }
    },
    parseStandardOptions: function(e) {
        var opts = {};
        opts.x=parseInt($(e).children('X').text());
        opts.y=parseInt($(e).children('Y').text());
        opts.width=parseInt($(e).children('Width').text());
        opts.height=parseInt($(e).children('Height').text());
        opts.id=$(e).children('Name').text();

        opts.dataBindings=[];
        $(e).children('DataBindings').children('DataBinding').each(function(idx,val) {
            var db=USS.parseDataBinding(val);
            opts.dataBindings.push(db);
        });
        return opts;
    },
    getParameters: function() {
        var paraList=[];
        for(var paraname in this.parameters) {
            var p=this.parameters[paraname];
            if (p.type=='ExternalDataSource') {
                paraList.push({name: p.name, namespace: p.namespace});
            }
        }
        return paraList;
    },
    updateBindings: function (pvals) {
        for(var i = 0; i < pvals.length; i++) {
            var p = pvals[i];
            var dbs = this.parameters[p.id.name];
            if (dbs && dbs.bindings) {
                for (var j = 0; j < dbs.bindings.length; j++) {
                    dbs.bindings[j].updateWidget(p);
                }
            }
        }
    },
    getComputations: function() {
        var compDefList=[];
        for(var paraname in this.parameters) {
            var p=this.parameters[paraname];
            if (p.type=='Computation') {
                var cdef={name: paraname, expression: p.expression, argument: [], language: 'jformula'};
                var args=p.args;
                for(var i=0;i<args.length;i++) {
                    var a=args[i];
                    cdef.argument.push({name: a.Opsname, namespace: 'MDB:OPS Name'});
                }
                compDefList.push(cdef);
            }
        }
        return compDefList;
    }
};

//basic widget, all the other ones are inheriting from this
USS.AbstractWidget = function() {};
USS.AbstractWidget.prototype = {
     updateValue: function(svg, para, usingRaw) {
         console.log("updateValue called on AbstractWidget. this:", this);
     },
     updatePosition: function(para, attribute, usingRaw) { //attribute is x or y
         var e=this.svg.getElementById(this.id);
         var newpos=USS.getParameterValue(para, usingRaw);
         e.setAttribute(attribute, newpos);
     },
     updatePositionByTranslation: function(svgid, para, attribute, usingRaw) { //attribute is x or y
         var e=this.svg.getElementById(svgid);
         var newpos=USS.getParameterValue(para, usingRaw);
         this[attribute]=newpos;
         e.setAttribute('transform', 'translate('+this.x+','+this.y+')');
     },
     updateFillColor: function(para, usingRaw) {
         var e=this.svg.getElementById(this.id);
         var newcolor=USS.getParameterValue(para, usingRaw);
         this.svg.configure(e, {stroke: newcolor});
     }
     
};

USS.Display.createWidgetTypes = function(widgetTypes) {
    //create empty constructors and prototypes derived from AbstractWidget
    for(var i=0; i<widgetTypes.length; i++) {
        var wt=widgetTypes[i];
        USS[wt] = function () {};
        USS[wt].prototype = new USS.AbstractWidget();
        USS[wt].prototype.constructor = USS[wt];
    }

    /************ Label **********/
    $.extend(USS.Label.prototype, {
        constructor: USS.Label,
        parseAndDraw: function (svg, parent, e) {
            var text=$("Text:first", e).text();
            var textStyle=$(e).children('TextStyle');
            USS.writeText(svg, parent, this, textStyle, text);
        }
    });
};

var widgetTypes=['Label', 'Field', 'Polyline', 'Rectangle', 'Symbol', 'ExternalImage', 'NavigationButton'];
USS.Display.createWidgetTypes(widgetTypes);


/************ Field **********/
$.extend(USS.Field.prototype, {
    decimals: 0,
    parseAndDraw: function(svg, parent, e) {
        //make a group to put the text and the bounding box together
        var settings = {
            transform: "translate("+this.x+","+this.y+")",
            class: "context-menu-field"
        };

        parent = svg.group(parent, this.id+'-group', settings);
        parent.ussWidget = this;

        var $e = $(e);
        var unit=$e.children('Unit').text();
        var decimals=$e.children('Decimals').text();
        if(decimals) {
            this.decimals=parseInt(decimals);
        }
        this.format=$e.children('Format').text();

        var odqi=$e.children('OverrideDQI');
        this.overrideDqi = odqi && (odqi.text().toLowerCase() === 'true');

        var showUnit=$e.children('ShowUnit').text().toLowerCase()==='true';
        var unitWidth=0;
        if(unit && showUnit) {
            var unitTextStyle=USS.parseTextStyle($(e).children('UnitTextStyle'));
            var ut=svg.text(parent, 0, 0, unit, unitTextStyle);

            var bbox=ut.getBBox();
            ut.setAttribute('dx', this.width - bbox.width);

            var unitVertAlignment=$e.children('UnitTextStyle').children('VerticalAlignment').text().toLowerCase();
            if(unitVertAlignment=='center') {
                ut.setAttribute('dy',  -bbox.y + (this.height - bbox.height)/2);
            } else if (unitVertAlignment=='top') {
                ut.setAttribute('dy',  -bbox.y);
            } else if (unitVertAlignment=='bottom') {
                ut.setAttribute('dy', -bbox.y+ (this.height - bbox.height));
            }
            unitWidth=bbox.width+2;
        }
        this.width-=unitWidth;
        var settings = {id: this.id+"-background"}; 
        if(!this.overrideDqi) {
             settings.class='dead-background';
        }

        var id = USS.getParameterFromWidget(this);
        var yamcsInstance = location.pathname.match(/\/([^\/]*)\/?/)[1];
        var rectLink = svg.link(parent, '/' + yamcsInstance + '/mdb/' + id.namespace + '/' + id.name, {});
        svg.rect(rectLink, 0, 0, this.width, this.height, settings);

        USS.writeText(svg, parent, {id: this.id, x: 0, y: 0, width: this.width, height: this.height}, $e.children('TextStyle'), " ");


    },
    updateValue: function(para) {
        var v = USS.getParameterValue(para, this.usingRaw);
        if(typeof v == 'number') {
            if(this.format) {
                v = sprintf(this.format, v);
            } else {
                v=v.toFixed(this.decimals);
            }
        }
        var svg = this.svg;
        var ftxt=svg.getElementById(this.id);
        if (!ftxt)
            return; // TODO temp until we unregister bindings upon window close
        ftxt.textContent = v;
        if(!this.overrideDqi) {
            var dqi=this.getDqi(para);
            svg.configure(ftxt, {class: dqi + "-foreground"});
            var fbg = svg.getElementById(this.id + "-background");
            svg.configure(fbg, {class: dqi+"-background"});
        }
    }, 
    updatePosition: function(para, attribute, usingRaw) {
        this.updatePositionByTranslation(this.id+"-group", para, attribute, usingRaw);
    },
    updateFillColor: function(para, usingRaw) {
        if(!this.overrideDqi) return;
        var newcolor=USS.getParameterValue(para, usingRaw);
        var svg = this.svg;
        var fbg = svg.getElementById(this.id + "-background");
        svg.configure(fbg, {fill: newcolor});
    },
    //the values are CSS classes defined in uss.dqi.css
    alldqi: ['dead', 'disabled', 'in_limits', 'nominal_limit_violation', 'danger_limit_violation', 'static', 'undefined'],
    //implements based on the mcs_dqistyle.xml
    getDqi: function (para) {
        switch(para.acquisitionStatus) {
            case 'ACQUIRED':
                switch(para.monitoringResult) {
                    case 'DISABLED':
                         return 'disabled';
                    case 'IN_LIMITS':
                         return 'in_limits';
                    case 'WATCH':
                    case 'WARNING':
                    case 'DISTRESS':
                         return 'nominal_limit_violation';
                    case 'CRITICAL':
                    case 'SEVERE':
                         return 'danger_limit_violation';
                    case undefined:
                         return 'undefined';
                }
                break;
            case 'NOT_RECEIVED': return 'dead';
            case 'INVALID': return 'dead';
            case 'EXPIRED': return 'expired';
        }
    }
});

/************* Polyline *****************/
$.extend(USS.Polyline.prototype, {
    parseAndDraw: function(svg, parent, e) {
        var points=[];
        $('Point', e).each(function(idx,val) {
            var px=$('x',val).text();
            var py=$('y',val).text();
            points.push([px,py]);
            });
        var settings={fill: "none"};
        var arrowStart=$(e).children('ArrowStart').text().toLowerCase()==='true';
        var arrowEnd=$(e).children('ArrowEnd').text().toLowerCase()==='true';
        if(arrowStart) {
            settings.markerStart="url(#uss-arrowStart)";
        }
        if(arrowEnd) {
            settings.markerEnd="url(#uss-arrowEnd)";
        }
        $.extend(settings, USS.parseDrawStyle(e));

        svg.polyline(parent, points, settings);
     }
});


/************* Rectangle *****************/
USS.Rectangle.prototype.parseAndDraw = function(svg, parent, e) {
    var settings=USS.parseFillStyle(e);
    $.extend(settings, USS.parseDrawStyle(e));
    if((settings.strokeWidth & 1) == 1) {
       this.x+=0.5;
       this.y+=0.5;
    }
    //console.log("Drawing rectangle with settings: ", settings);
    svg.rect(parent, this.x, this.y, this.width, this.height, settings);
};

/************* Symbol *****************/
$.extend(USS.Symbol.prototype, {
    symbolLibrary: {},
    parseAndDraw: function(svg, parent, e) {
        //svg.image(parent, opts.x, opts.y, opts.width, opts.height, "U19_led_3D_grey.svg");
        var libraryName=$(e).children('LibraryName').text();
        var symbolName=$(e).children('SymbolName').text();

        var settings={id: this.id, class: 'uss-symbol'};
        var img=svg.image(parent, this.x, this.y, this.width, this.height, "", settings);
        this.libraryName=libraryName;
        this.symbolName=symbolName;

        var sl=this.symbolLibrary[libraryName];
        if(sl === undefined) {
            this.loadSymbolLibrary(libraryName);
        } 
        //TODO this should be async after the library has been loaded
        sl=this.symbolLibrary[libraryName];
        if (sl && sl.loaded) {
            var s = sl[symbolName];
            if(s === undefined) {
                console.log('Cannot find symbol '+symbolName+' in library '+libraryName);
            } else {
                this.symbol=s;
                img.setAttribute('href', "/_static/symlib/images/"+s.defaultImage);
            }
        }
    },
    loadSymbolLibrary: function(libraryName) {
        //console.log("loading symbol library ", libraryName);
        var sl = {};
        sl.loaded=false;
        this.symbolLibrary[libraryName]=sl;
        $.ajax({
            url: "/_static/symlib/"+libraryName+".xml",
            async: false
        }).done(function(xmlData) {
            $('library symbol', xmlData).each(function(idx, val) {
                var s=new Object();
                s.type=$(val).children('type').text();
                s.name=$(val).children('name').text();
                s.states = {};
                $('image', val).each(function(idx,val1) {
                    var state=val1.getAttribute('state');
                    var img=$(val1).text();
                    if(state) {
                        s.states[state]=img;
                    }
                    if(s.type=='dynamic') {
                        var def=val1.getAttribute('default').toLowerCase()=='true';
                        if(def) {
                            s.defaultImage=img;
                        }
                    } else {
                         s.defaultImage=img;
                    }
                });
                sl[s.name]=s;
            });
            sl.loaded=true;
         });
     },
     updateValue: function(para, usingRaw) {
        var value=USS.getParameterValue(para, usingRaw);
        var img=this.symbol.states[value];
        if(img === undefined) {
            img = symbol.defaultImage;
         }
        var svgimg=this.svg.getElementById(this.id);
        if(svgimg) svgimg.setAttribute('href', "/_static/symlib/images/"+img);
         
     }
});

/************* ExternalImage *****************/
$.extend(USS.ExternalImage.prototype, {
    parseAndDraw: function(svg, parent, e) {
        var pathname=$(e).children('Pathname').text();
        svg.image(parent, this.x, this.y, this.width, this.height, "/_static/"+pathname);
    }
});


/************* NavigationButton *****************/
$.extend(USS.NavigationButton.prototype, {
    
    parseAndDraw: function(svg, parent, e) {
        var $e = $(e);
        var pressCmd = $e.children("PressCommand")[0];
        var cmdClass = pressCmd.getAttribute("class");
        var cmd;
        if(cmdClass == "OpenDisplayCommand") {
            var displayBaseName = $(pressCmd).children("DisplayBasename").text();
            cmd = "USS.openDisplay('"+displayBaseName+"')";
        } else if(cmdClass == "CloseDisplayCommand") {            
            cmd = "USS.closeDisplay()";
        } else {
            console.log("Unsupported command class "+cmdClass);
            return;
        }
        
        
        var settings=USS.parseFillStyle(e);
        $.extend(settings, {
            strokeOpacity: 1,
            stroke: "rgba(0,0,0,255)",
            strokeWidth: "1.0"
        });
        if((settings.strokeWidth & 1) == 1) {
            this.x+=0.5;
            this.y+=0.5;
        }
        parent = svg.group(parent, this.id+'-group', {cursor: "pointer", onmouseup: cmd});        
        svg.rect(parent, this.x, this.y, this.width, this.height, settings);
        var $label = $e.children("ReleasedCompound").children("Elements").children("Label");
        var textStyle = $label.children('TextStyle');
        var text = $label.children('Text').text();
        USS.writeText(svg, parent, this, textStyle, text);        
    }
});

}());
