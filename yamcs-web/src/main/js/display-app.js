var yamcsInstance=location.pathname.match(/\/([^\/]*)\//)[1];
var loadedDisplays = {};
var openWindows = {};
var lastSelectedWindow = null;

var yamcsWebSocket

$(document).ready(function(){
    configureMenu();
    configureContextMenu();
    loadDisplayList();
    $.window.prepare({
        animationSpeed: 200,  // set animation speed
        minWinLong: 180,       // set minimized window long dimension width in pixel
        scrollable: false
     });


     
    
    yamcsWebSocket = new YamcsWebSocket(yamcsInstance);
});

function configureMenu() {
     var b=$("#button3");
     b.bind( "mouseenter.button", function() {
         b.addClass( "ui-state-hover" );
     }).bind( "mouseleave.button", function() {
         $( b ).removeClass( "ui-state-hover" );
     }).bind( "click.button", function( event ) {
           alert("bumburum");
     });

}

function configureContextMenu() {
     $.contextMenu({
        selector: '[class~=context-menu-field]', 
        trigger: 'right',
        callback: function(key, options) {
            console.log('clicked', key, options);
	    parameter = USS.getParameterFromWidget(options.$trigger[0].ussWidget);
	    if( key== 'info') {
	        showParameterInfo(parameter)	      
	    } else {
	        alert(key+" not implemented");	      
	    }
        },
        items: {
            "info": {name: "Info", icon: "info"},
            "plot": {name: "Plot", icon: "plot"},            
        }
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
         url: "/"+yamcsInstance+"/displays/listDisplays",
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

function showWindow(name, onShowFunction, resizable, scrollable) {

  if (typeof(resizable)==='undefined') resizable = false;
   if (typeof(scrollable)==='undefined') scrollable = false;
  
  var divid = 'wnd-'+name;
  wnd = $("#yamcs-body").window({
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
		 onShowFunction(div, name, yamcsWebSocket, function(width, height) {
                     console.log("resizing to ", width, height);
                     wnd.resize(width, height+25); //the 24 comes from  css .window_header_normal height+2*padding: 20*2*2
                     var s="<span class='yamcs-task' id='task-"+name+"'><img src='/_static/symlib/images/U23_led_grey.svg' height='12' width='12' class='yamcs-task-icon' >"+name+"</span>";
                     $("#yamcs-task-list").append(s);
                     var e = document.getElementById("task-"+name);
                     var $e = $(e);s
                     $e.bind( "mouseenter.button", function() {
                         $e.addClass( "ui-state-hover" );
                     }).bind( "mouseleave.button", function() {
                         $e.removeClass( "ui-state-hover" );
                     }).bind( "click.button", function( event ) {
                         bringWindowToFront(name);
                     });
                     document.body.style.cursor='default';
                 });		 		
            }, 
            onClose: function(wnd) {
                for(var f in openWindows) {
                    if(openWindows[f] === wnd) {
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
        showWindow(filename, function(div, filename, yamcsWebSocket, onLoadContent) {
	        USS.loadDisplay(div, filename, yamcsWebSocket, function(displ) {
	            onLoadContent(displ.width, displ.height);
	        });
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
  var name = 'pinfo-'+parameter.name;
  showWindow(name, function(div, name, yamcsWebSocket, onLoadContent) {
      console.log("getting parameter info for ", parameter);      
      $.ajax({
	    url: "/"+yamcsInstance+"/api/mdb/parameterInfo",
            data: parameter,
      }).done(function(pinfo) {		 
	  $(div).html("<pre class='yamcs-pinfo'>"+JSON.stringify(pinfo, null, '  ')+"</pre>");
          onLoadContent(800,500);
      }).fail( function(xhr, textStatus, errorThrown) {
	var r = JSON.parse(xhr.responseText);
	$(div).html("<pre class='yamcs-pinfo'> ERROR:\n"+JSON.stringify(r, null, '  ')+"</pre>");
          onLoadContent(800,500);
      })
  }, true, true);
}
