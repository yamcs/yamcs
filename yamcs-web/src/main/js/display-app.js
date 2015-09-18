'use strict';

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
        var parameter = USS.getParameterFromWidget(e.currentTarget.ussWidget);
        showParameterDetail(parameter);
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
            event.preventDefault();
            return false;
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
        onSelect: function () {
            document.title = name;
        },
        onUnselect: function () {
            document.title = 'Web Displays';
        },
        onClose: function(wnd) {
            for(var f in openWindows) {
                if(openWindows[f] === wnd) {
                    onCloseCallback(webSocketClient);
                    closeWindow(f);
                }
            }
            document.title = 'Web Displays';
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

function updateGraph(g, data) {
    var options = { file: data };
    if (data.length > 50) {
        options.drawPoints = false;
        options.showRoller = false;
    }
    g.updateOptions(options);
}

var archiveFetched = false;

function showHistoricData(g, parameter, plotMode, data) {
    var now = new Date();
    var nowIso = now.toISOString();
    var before = new Date(now.getTime());
    var beforeIso = nowIso;
    if (plotMode === '15m') {
        before.setMinutes(now.getMinutes() - 15);
        beforeIso = before.toISOString();
    } else if (plotMode === '30m') {
        before.setMinutes(now.getMinutes() - 30);
        beforeIso = before.toISOString();
    } else if (plotMode === '1h') {
        before.setHours(now.getHours() - 1);
        beforeIso = before.toISOString();
    } else if (plotMode === '5h') {
        before.setHours(now.getHours() - 5);
        beforeIso = before.toISOString();
    } else if (plotMode === '1d') {
        before.setDate(now.getDate() - 1);
        beforeIso = before.toISOString();
    } else if (plotMode === '1w') {
        before.setDate(now.getDate() - 7);
        beforeIso = before.toISOString();
    }

    archiveFetched = false;
    $.ajax({
        type: 'POST',
        url: '/'+yamcsInstance+'/api/archive',
        data: JSON.stringify({
            utcStart: beforeIso.slice(0, -1),
            utcStop: nowIso.slice(0, -1),
            parameterRequest: {
                nameFilter: [parameter],
                sendRaw: true
            }
        })
    }).done(function(response) {
        data.length = 0; // clear first
        for (var i = 0; i < response['parameterData'].length; i++) {
            var pdata = response['parameterData'][i];
            for (var j = 0; j < pdata['parameter'].length; j++) {
                var p = pdata['parameter'][j];
                var t = new Date();
                t.setTime(Date.parse(p['generationTimeUTC']));
                var v = USS.getParameterValue(p, true);
                data.push([t, v]);
            }
        }
        updateGraph(g, (data.length > 0) ? data : 'x\n');
        console.log('fetched');
        archiveFetched = true;
    });
}

function showParameterDetail(parameter) {
    var name = parameter.name;
    var wnd = openWindows[name];
    if (wnd) {
        wnd.select();
    } else {
        $.get('/_static/parameter.html', function(templateData) {
            var plotMode = '30m';
            showWindow(name, {
                onopen: function(divEl, name, webSocketClient, onLoadContent) {
                    console.log('getting parameter detail for ', parameter);
                    $.ajax({
                        url: '/'+yamcsInstance+'/api/mdb/parameterInfo',
                        data: parameter
                    }).done(function(pinfo) {
                        //$(div).html("<pre class='yamcs-pinfo'>"+JSON.stringify(pinfo, null, '  ')+"</pre>");
                        console.log('pinfo', pinfo);
                        var template = swig.compile(templateData);

                        var div = $(divEl);
                        div.html(template({ pinfo: pinfo, plotMode: plotMode }));
                        var graphDiv = div.find('.graphdiv')[0];
                        var data = [];
                        var g = new Dygraph(graphDiv, 'X\n', {
                            drawPoints: true,
                            showRoller: true,
                            gridLineColor: 'lightgray',
                            axisLabelColor: '#666',
                            axisLabelFontSize: 11,
                            labels: ['Time', 'Value']
                        });

                        var latestUpdate = div.find('.para-latest-update')[0];
                        var generated = div.find('.para-generated')[0];
                        var status = div.find('.para-status')[0];

                        // Subscribe realtime
                        var tempData = []; // Store while processing archive
                        webSocketClient.bindDataHandler('PARAMETER', function(pdata) {
                            var params = pdata['parameter'];
                            for(var i=0; i<params.length; i++) {
                                var p = params[i];
                                if (p.id.name === parameter.name) {
                                    var t = new Date();
                                    t.setTime(Date.parse(p['generationTimeUTC']));
                                    var v = USS.getParameterValue(p, true);
                                    if (archiveFetched) {
                                        latestUpdate.innerText = v;
                                        generated.innerText = p['generationTimeUTC'];
                                        //status.innerText = 'bla';
                                        data.push([t, v]);
                                        updateGraph(g, data);
                                    } else {
                                        tempData.push([t, v]);
                                        // TODO do something with tempData
                                    }
                                    break;
                                }
                            }
                        });

                        showHistoricData(g, parameter, plotMode, data);
                        div.find('.plotrange').on('click', function(e) {
                            $(this).siblings().removeClass('nolink');
                            $(this).addClass('nolink');
                            var newPlotMode = e.target.innerText;
                            showHistoricData(g, parameter, newPlotMode, data);
                        });
                        onLoadContent(800,500);
                    }).fail(function(xhr, textStatus, errorThrown) {
                        var r = JSON.parse(xhr.responseText);
                        $(div).html("<pre class='yamcs-pinfo'> ERROR:\n"+JSON.stringify(r, null, '  ')+"</pre>");
                        onLoadContent(800,500);
                    });
                },
                onclose: function(webSocketClient) {
                    // TODO unregister handler or sth
                },
                resizable: false,
                scrollable: true
            });
        });
    }
}
