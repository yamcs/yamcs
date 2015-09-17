var yamcsInstance=location.pathname.match(/\/([^\/]*)\//)[1];
var loadedDisplays = {};
var openWindows = {};
var lastSelectedWindow = null;

var webSocketClient;

$(document).ready(function() {
    configureMenu();
    configureContextMenu();
    loadDisplayList();
    $.window.prepare({
        animationSpeed: 200,  // set animation speed
        minWinLong: 180,       // set minimized window long dimension width in pixel
        scrollable: false
    });
    
    webSocketClient = yamcsWebSocket(yamcsInstance);
    webSocketClient.bindDataHandler('PARAMETER', function(pdata) {
        var params=pdata.parameter;
        for(var i=0; i<params.length; i++) {
            var p=params[i];
            var dbs = webSocketClient.subscribedParameters[p.id.name];
            if(!dbs) {
                console.log("cannot find bindings for "+ p.id.name, webSocketClient.subscribedParameters);
                continue;
            }
            for(var j = 0; j < dbs.length; j++){
                USS.updateWidget(dbs[j], p);
            }
        }
    });
});

function configureMenu() {
     var b=$("#button3");
     b.bind("mouseenter.button", function() {
         b.addClass("ui-state-hover");
     }).bind("mouseleave.button", function() {
         $(b).removeClass("ui-state-hover");
     }).bind("click.button", function(event) {
         alert("bumburum");
     });
}

function configureContextMenu() {
    $(document).on('click', '[class~=context-menu-field]', function(e) {
        console.log('hm', e.currentTarget);
        var parameter = USS.getParameterFromWidget(e.currentTarget.ussWidget);
        showParameterPlot(parameter);
    });
}

function loadDisplayList() {
    function addDisplay(sb, path, d) {
         if(d instanceof Array) {
             sb.push('<li><a href="#">'+d[0]+'</a>');
             sb.push("<ul>");
             for(var i=1; i<d.length; i++) {
                 var d1=d[i];
                 addDisplay(sb, path+d[0]+"/", d1);
             }
             sb.push("</ul>");
         } else {
              sb.push('<li data-display="'+path+d+'"><a href="#">'+d+'</a>');
         }
         sb.push("</li>");
    }

    $.ajax({
         url: "/"+yamcsInstance+"/displays/listDisplays"
    }).done(function(dlist) {
         var sb=[];
         for(var i=0; i<dlist.length; i++) {
             var d=dlist[i];
             addDisplay(sb, "", d);
         }
         $("#yamcs-displays-menu").append(sb.join(""));
         updateMenu();
    });
}

function updateMenu() {
    $("#yamcs-menu").menubar({
        position: {
            within: $("#yamcs-frame").add(window).first()
        },
        select: function(event, ui) {

            var filename = $(ui.item).data('display');
            if(!filename) return;
            showDisplay(filename);
        }
    });
}

function showWindow(name, options) {
    var onShowCallback = options['onopen'] || function() {};
    var onCloseCallback = options['onclose'] || function() {};
    var resizable = options.resizable || false;
    var scrollable = options.scrollable || false;
  
    var divid = 'wnd-'+name;
    var wnd = $("#yamcs-body").window({
        title: name,
        content: '<div id="'+divid+'"></div>',
        x:110, y:30,
        showFooter: false,
        resizable: resizable,
        minimizable: false,
        maximizable: false,
        checkBoundary: false,
        scrollable: scrollable,
	    maxWidth: -1,
	    maxHeight: -1,
        onShow: function(wnd) {
            document.body.style.cursor='wait';
            var div=document.getElementById(divid);
            onShowCallback(div, name, webSocketClient, function(width, height) {
                console.log("resizing to ", width, height);
                wnd.resize(width, height+25); //the 24 comes from  css .window_header_normal height+2*padding: 20*2*2
                var s="<span class='yamcs-task' id='task-"+name+"'><img src='/_static/images/undef.png' class='yamcs-task-icon' >"+name+"</span>";
                $("#yamcs-task-list").append(s);
                var e = document.getElementById("task-"+name);
                var $e = $(e);
                $e.bind("mouseenter.button", function() {
                    $e.addClass("ui-state-hover");
                }).bind("mouseleave.button", function() {
                    $e.removeClass("ui-state-hover");
                }).bind("click.button", function(event) {
                    bringWindowToFront(name);
                });
                document.body.style.cursor='default';
            });
        },
        onClose: function(wnd) {
            for(var f in openWindows) {
                if(openWindows[f] === wnd) {
                    onCloseCallback(webSocketClient);
                    closeWindow(f);
                }
            }
        }
    });
    openWindows[name] = wnd;
}

function showDisplay(filename) {    
    var wnd = openWindows[filename];
    if(wnd) {
        wnd.select();
    } else {
        var dname=filename.substring(0, filename.length-4);
        var display;
        showWindow(filename, {
            onopen: function(div, filename, yamcsWebSocket, onLoadContent) {
                USS.loadDisplay(div, filename, yamcsWebSocket, function(displ) {
                    display = displ;
                    onLoadContent(displ.width, displ.height);
                });
            },
            onclose: function(yamcsWebSocket) {
                //console.log('got params', display.parameters);
                //yamcsWebSocket.unregisterParameterBindings(display.parameters);
            }
        });
    }
}

function closeWindow(name) {
    delete openWindows[name];
    var e = document.getElementById("task-"+name);
    $(e).remove();
}

function bringWindowToFront(name) {
    console.log(" bringing "+name+" in front");
    if(lastSelectedWindow) lastSelectedWindow.unselect();
    lastSelectedWindow = openWindows[name];
    lastSelectedWindow.select();
}

function showParameterInfo(parameter) {
    var name = parameter.name + ' :: Info';
    showWindow(name, {
        onopen: function(div, name, webSocketClient, onLoadContent) {
            console.log("getting parameter info for ", parameter);
            $.ajax({
                url: "/"+yamcsInstance+"/api/mdb/parameterInfo",
                data: parameter
            }).done(function(pinfo) {
                $(div).html("<pre class='yamcs-pinfo'>"+JSON.stringify(pinfo, null, '  ')+"</pre>");
                onLoadContent(800,500);
            }).fail(function(xhr, textStatus, errorThrown) {
                var r = JSON.parse(xhr.responseText);
                $(div).html("<pre class='yamcs-pinfo'> ERROR:\n"+JSON.stringify(r, null, '  ')+"</pre>");
                onLoadContent(800,500);
            });
        },
        resizable: true,
        scrollable: true
    });
}

function showParameterPlot(parameter) {
    $.get('/_static/parameter.html', function(templateData) {
        var name = parameter.name;
        showWindow(name, {
            onopen: function(divEl, name, webSocketClient, onLoadContent) {
                console.log("getting parameter info for ", parameter);
                $.ajax({
                    url: '/'+yamcsInstance+'/api/mdb/parameterInfo',
                    data: parameter
                }).done(function(pinfo) {
                    //$(div).html("<pre class='yamcs-pinfo'>"+JSON.stringify(pinfo, null, '  ')+"</pre>");
                    var template = swig.compile(templateData);

                    var div = $(divEl);
                    div.html(template({ pinfo: pinfo }));
                    var graphDiv = div.find('.graphdiv')[0];
                    var data = [];
                    var g = new Dygraph(graphDiv, 'X\n', {
                        drawPoints: true,
                        showRoller: true,
                        axisLabelFontSize: 11,
                        labels: ['Time', 'Value']
                    });

                    webSocketClient.bindDataHandler('PARAMETER', function(pdata) {
                        var params = pdata['parameter'];
                        for(var i=0; i<params.length; i++) {
                            var p = params[i];
                            if (p.id.name === parameter.name) {
                                var t = new Date();
                                t.setTime(Date.parse(p['generationTimeUTC']));
                                var v = USS.getParameterValue(p, true);
                                data.push([t, v]);
                                if (data.length == 50) {
                                    g.updateOptions({ drawPoints: false, showRoller: false });
                                }
                                g.updateOptions({ file: data });
                                break;
                            }
                        }
                    });

                    onLoadContent(800,500);
                }).fail(function(xhr, textStatus, errorThrown) {
                    console.log("uh", xhr, textStatus, errorThrown);
                    var r = JSON.parse(xhr.responseText);
                    $(div).html("<pre class='yamcs-pinfo'> ERROR:\n"+JSON.stringify(r, null, '  ')+"</pre>");
                    onLoadContent(800,500);
                });
            },
            onclose: function(webSocketClient) {
                // TODO unregister handler or sth
            },
            resizable: true,
            scrollable: true
        });
    });
}
