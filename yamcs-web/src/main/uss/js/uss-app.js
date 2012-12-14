var yamcsInstance=location.pathname.match(/\/([^\/]*)\//)[1];
var loadedDisplays = {};
var lastSelectedDisplay = null;

var yamcsWebSocket

$(document).ready(function(){
    configureMenu();
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

function showDisplay(filename) {
    
    var wnd=loadedDisplays[filename];
    if(wnd) {
        wnd.select();
    } else {
        //var filename="ACES_FS_Overview.uss";
        wnd = $("#yamcs-body").window({
            title: filename,
            content: '<div id="'+filename+'"></div>',
            //width: 300, height: 300,
            x:110, y:30,
            showFooter: false,
            resizable: false,
            minimizable: false,
            maximizable: false,
            checkBoundary: true,
            scrollable: false,
            onShow: function(wnd) {
                 document.body.style.cursor='wait';
                 var div=document.getElementById(filename);
                 USS.loadDisplay(div, filename, yamcsWebSocket, function(displ) {
                     console.log("resizing to ", displ.width, displ.height);
                     wnd.resize(displ.width, displ.height+25); //the 24 comes from  css .window_header_normal height+2*padding: 20*2*2
                     var dname=filename.substring(0, filename.length-4);
                     //var s="<tr class='yamcs-task'><td><img src='symlib/images/U23_led_grey.svg' height='12' width='12' ></td><td id='task-"+filename+"' class='yamcs-task'>"+dname+"</td></tr>";
                     var s="<span class='yamcs-task' id='task-"+filename+"'><img src='/_static/symlib/images/U23_led_grey.svg' height='12' width='12' class='yamcs-task-icon' >"+dname+"</span>";
        
                     $("#yamcs-task-list").append(s);
                     var e = document.getElementById("task-"+filename);
                     var $e = $(e);
                     $e.bind( "mouseenter.button", function() {
                         $e.addClass( "ui-state-hover" );
                     }).bind( "mouseleave.button", function() {
                         $e.removeClass( "ui-state-hover" );
                     }).bind( "click.button", function( event ) {
                         bringDisplayToFront(filename);
                     });
                     document.body.style.cursor='default';
                 });
            }, 
            onClose: function(wnd) {
                for(var f in loadedDisplays) {
                    if(loadedDisplays[f] === wnd) {
                        closeDisplay(f);
                    }
                }
            }
        });
        loadedDisplays[filename] = wnd;
    }
}

function closeDisplay(filename) {
    delete loadedDisplays[filename];
    var e = document.getElementById("task-"+filename);
    $(e).remove();
}

function bringDisplayToFront(filename) {
    console.log(" bringing "+filename+" in front");
    if(lastSelectedDisplay) lastSelectedDisplay.unselect();
    lastSelectedDisplay=loadedDisplays[filename];
    lastSelectedDisplay.select();
}
