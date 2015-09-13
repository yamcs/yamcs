/**
 * jQuery Window Plugin - To Popup A Beautiful Window-like Dialog
 * http://fstoke.me/jquery/window/
 * Copyright(c) 2011 David Hung
 * Dual licensed under the MIT and GPL licenses
 * Version: 5.03
 * Last Revision: 2011-04-30
 *
 * The window status is defined as: cascade(default), minimized, maxmized
 * 
 * The code style is reference from javascript Singleton design pattern. Please see:
 * http://fstoke.me/blog/?page_id=1610
 * 
 * Join the facebook fans page to discuss there and get latest information.
 * http://www.facebook.com/pages/jQuery-Window-Plugin/116769961667138
 *
 * This jQuery plugin has been tested in the following browsers:
 * - IE 7, 8
 * - Firefox 3.5+
 * - Opera 9, 10+
 * - Safari 4.0+
 * - Chrome 2.0+
 *
 * Required jQuery Libraries:
 * jquery.js         (v1.3.2)
 * jquery-ui.js      (v1.7.2)
 *
 * Customized Button JSON Array Sample:
	var myButtons = [
		// facebook button
		{
		id: "btn_facebook",           // required, it must be unique in this array data
		title: "share to facebook",   // optional, it will popup a tooltip by browser while mouse cursor over it
		clazz: "my_button",           // optional, don't set border, padding, margin or any style which will change element position or size
		style: "",                    // optional, don't set border, padding, margin or any style which will change element position or size
		image: "img/facebook.gif",    // optional, the image url of button icon(16x16 pixels)
		callback:                     // required, the callback function while click it
			function(btn, wnd) {
				wnd.getContainer().find("#demo_text").text("Share to facebook!");
				wnd.getContainer().find("#demo_logo").attr("src", "img/facebook_300x100.png");
			}
		},
		// twitter button
		{
		id: "btn_twitter",
		title: "share to twitter",
		clazz: "my_button",
		style: "background:#eee;",
		image: "img/twitter.png",
		callback:
			function(btn, wnd) {
				wnd.getContainer().find("#demo_text").text("Share to twitter!");
				wnd.getContainer().find("#demo_logo").attr("src", "img/twitter_300x100.jpg");
			}
		}
	];
 */
	
// Get window instance via jQuery call
// create window on html body
$.window = function(options) {
	return $.Window.getInstance(null, options);
};

// create window on caller element
$.fn.window = function(options) {
	return $.Window.getInstance($(this), options);
}

// Creating Window Dialog Module
$.Window = (function()  {
	// static private methods
	// static constants
	var VERSION = "5.03";           // the version of current plugin
	var ICON_WH = 16;               // window icon button width/height, in pixels. check "window_icon_button" style in css
	var ICON_MARGIN = 4;            // window icon button margin, in pixels. check "window_icon_button" style in css
	var ICON_OFFSET = ICON_WH + ICON_MARGIN; // window icon button offset for decide function bar width in header panel
	var OPACITY_MINIMIZED = 0.7;    // css opacity while window minimized or doing animation
	var MINIMIZED_NARROW = 24;
	var MINIMIZED_LONG = 120;
	var RESIZE_EVENT_DELAY = 200;
	var ua = navigator.userAgent.toLowerCase(); // browser useragent
	
	// static variables
	var windowIndex = 0;            // index to create window instance id
    var maxZIndex=2000;
	var windowStorage = [];         // a array to store created window instance
	var initialized = false;        // a boolean flag to check is it initialized?
	var resizeTimer = null;         // a timer to avoid doing duplicated routine while receiving browser window resize event
	var parentCallers = [];         // a storage to save caller object
	var minWinData = {              // global minimized window data
		long: MINIMIZED_LONG,
		storage: []                 // a storage to save minimized window instance
	};
	
	// the static setting
	var setting = {
		dock: 'left',                   // [string:"left"] the direction of minimized window dock at. the available values are [left, right, top, bottom]
		dockArea: null,                 // [jquery object, element:null] the area which the windows will dock at
		animationSpeed: 400,            // [number:400] the speed of animations: maximize, minimize, restore, shift, in milliseconds
		minWinNarrow: MINIMIZED_NARROW, // [number:24] the narrow dimension of minimized window
		minWinLong: MINIMIZED_LONG,     // [number:120] the long dimension of minimized window
		handleScrollbar: true,          // [boolean:true] to handle browser scrollbar when window status changed(maximize, minimize, cascade)
		showLog: false                  // [boolean:false] to decide show log in firebug, IE8, chrome console
	};
	
	// select the current clicked window instance, concurrently, unselect last selected window instance
	function selectWindow(parent, wnd) {
		wnd.select();
	}
	
	// get the window instance
	function getWindow(windowId) {
		for( var i=0, len=windowStorage.length; i<len; i++ ) {
			var wnd = windowStorage[i];
			if( wnd.getWindowId() == windowId ) {
				return wnd;
			}
		}
	}
	
	// push the window instance into storage
	function pushWindow(wnd) {
		windowStorage.push(wnd);
	}
	
	// pop the window instance from storage out
	function popWindow(wnd) {
		for( var i=0, len=windowStorage.length; i<len; i++ ) {
			var w = windowStorage[i];
			if( w == wnd ) {
				windowStorage.splice(i--,1); // remove array element
				break;
			}
		}
	}
	
	// push the window instance into minimized storage
	function pushMinWindow(parent, wnd) {
		if( setting.dockArea != null ) {
			parent = $(setting.dockArea);
		}
		if( parent != null ) {
			parent.get(0)._minWinData.storage.push(wnd);
		} else {
			minWinData.storage.push(wnd);
		}
	}
	
	// pop the window instance from minimized storage out
	function popMinWindow(parent, wnd) {
		var doAdjust = false;
		parent = (setting.dockArea != null)? $(setting.dockArea):parent;
		var storage = (parent != null)? parent.get(0)._minWinData.storage:minWinData.storage;
		for( var i=0; i<storage.length; i++ ) {
			var w = storage[i];
			if( w == wnd ) {
				storage.splice(i--,1); // remove array element
				doAdjust = true;
				continue;
			}
			if( doAdjust ) {
				w._decreaseMiniIndex();
			}
		}
	}
	
	// get the minimized window count by parent elemen
	function getMinWindowLength(parent) {
		parent = (setting.dockArea != null)? $(setting.dockArea):parent;
		var storage = (parent != null)? parent.get(0)._minWinData.storage:minWinData.storage;
		return storage.length;
	}
	
	function checkMinWindowSize(parent, bPush) {
		var bAdjust = false;
		var rect = null;
		var mwdata = null;
		
		// set boundary rect and minimized window data
		if( setting.dockArea != null ) {
			parent = $(setting.dockArea);
		}
		if( parent != null ) {
			rect = {width:parent.innerWidth(), height:parent.innerHeight()};
			mwdata = parent.get(0)._minWinData;
		} else {
			rect = getBrowserScreenWH();
			mwdata = minWinData;
		}
		
		var count = getMinWindowLength(parent);
		if( setting.dock == 'left' || setting.dock == 'right' ) {
			if( bPush ) {
				if( ((count+1) * mwdata.long) > rect.height ) {
					mwdata.long = rect.height/(count+1);
					adjustAllMinWindows(parent);
				}
			} else if( mwdata.long < setting.minWinLong ) {
				if( (count * setting.minWinLong) < rect.height ) {
					mwdata.long = setting.minWinLong;
				} else {
					mwdata.long = rect.height/count;
				}
			}
		} else if( setting.dock == 'top' || setting.dock == 'bottom' ) {
			if( bPush ) {
				if( ((count+1) * mwdata.long) > rect.width ) {
					mwdata.long = rect.width/(count+1);
					adjustAllMinWindows(parent);
				}
			} else if( mwdata.long < setting.minWinLong ) {
				if( (count * setting.minWinLong) < rect.width ) {
					mwdata.long = setting.minWinLong;
				} else {
					mwdata.long = rect.width/count;
				}
			}
		}
	}
	
	function adjustAllMinWindows(parent) {
		parent = (setting.dockArea != null)? $(setting.dockArea):parent;
		var storage = (parent != null)? parent.get(0)._minWinData.storage:minWinData.storage;
		for( var i=0; i<storage.length; i++ ) {
			storage[i]._adjustMinimizedPos(false);
		}
	}
	
	// hide browser scroll bar
	function hideBrowserScrollbar() {
		if( setting.handleScrollbar ) {
			if( ua.indexOf("msie 7") >= 0 ) { // fix IE7
				$("body").attr("scroll", "no");
			} else {
				document.body.style.overflow = "hidden";
			}
		}
	}
	
	// show browser scroll bar
	function showBrowserScrollbar() {
		if( setting.handleScrollbar ) {
			if( ua.indexOf("msie 7") >= 0 ) { // fix IE7
				$("body").removeAttr("scroll");
			} else {
				document.body.style.overflow = "auto";
			}
		}
	}
	
	function getBrowserScreenWH() {
		var width = document.documentElement.clientWidth;
		var height = document.documentElement.clientHeight;
		return {width:width, height:height};
	}

	function getBrowserScrollXY() {
		var scrOfX = 0, scrOfY = 0;
		if( typeof( window.pageYOffset ) == 'number' ) {
			//Netscape compliant
			scrOfY = window.pageYOffset;
			scrOfX = window.pageXOffset;
		} else if( document.body && ( document.body.scrollLeft || document.body.scrollTop ) ) {
			//DOM compliant
			scrOfY = document.body.scrollTop;
			scrOfX = document.body.scrollLeft;
		} else if( document.documentElement && ( document.documentElement.scrollLeft || document.documentElement.scrollTop ) ) {
			//IE6 standards compliant mode
			scrOfY = document.documentElement.scrollTop;
			scrOfX = document.documentElement.scrollLeft;
		}
		return {left:scrOfX, top:scrOfY};
	}
	
	function getParentPanelStartPos(parent, bWithoutCheckAbsolute) {
		var pos = null;
		if( parent != null ) {
			// check panel is absolute position?
			var bAbsolute = (parent.css('position') == 'absolute');
			if( bAbsolute && !bWithoutCheckAbsolute ) {
				pos = {left:0, top:0};
			} else {
				pos = parent.offset();
				var bTop = parseInt( parent.css('borderTopWidth') );
				var bLeft = parseInt( parent.css('borderLeftWidth') );
				pos.left += bLeft;
				pos.top += bTop;
			}
			log( 'start pos: '+pos.left+','+pos.top);
		}
		return pos;
	}
	
	function getCssStyleByDock(parent, miniIndex) {
		var targetCss = {};
		var screenWH = getBrowserScreenWH();
		var cpos = null;
		var narrow = setting.minWinNarrow;
		var long = minWinData.long;
		
		if( setting.dockArea != null ) {
			// check original parent panel
			var pOffset = {left:0, top:0};
			if( parent != null ) {
				var bAbsolute = (parent.css('position') == 'absolute');
				if( bAbsolute ) {
					pOffset = getParentPanelStartPos(parent, true);
				}
			}
			
			// set parent variable as customerized dock area panel
			parent = $(setting.dockArea);
			cpos = getParentPanelStartPos(parent, true);
			// subtract the offset distance
			cpos.left -= pOffset.left;
			cpos.top -= pOffset.top;
			long = parent.get(0)._minWinData.long;
		} else if( parent != null ) {
			cpos = getParentPanelStartPos(parent);
			long = parent.get(0)._minWinData.long;
		}
		
		if( setting.dock == 'left' || setting.dock == 'right' ) {
			targetCss.width = narrow;
			targetCss.height = long - 1;
			targetCss.top = miniIndex * long;
			if( setting.dock == 'left' ) {
				if( parent != null ) {
					targetCss.top += cpos.top;
					targetCss.left = cpos.left;
				} else {
					targetCss.left = 0;
				}
			} else if( setting.dock == 'right' ) {
				if( parent != null ) {
					targetCss.top += cpos.top;
					targetCss.left = cpos.left + parent.width() - narrow - 2;
				} else {
					targetCss.left = screenWH.width - narrow;
				}
			}
		} else if( setting.dock == 'top' || setting.dock == 'bottom' ) {
			targetCss.width = long - 1;
			targetCss.height = narrow;
			targetCss.left = miniIndex * long;
			if( setting.dock == 'top' ) {
				if( parent != null ) {
					targetCss.top = cpos.top;
					targetCss.left += cpos.left;
				} else {
					targetCss.top = 0;	
				}
			} else if( setting.dock == 'bottom' ) {
				if( parent != null ) {
					targetCss.top = cpos.top + parent.height() - narrow - 2;
					targetCss.left += cpos.left;
				} else {
					targetCss.top = screenWH.height - narrow;
				}
			}
		}
		log( targetCss );
		return targetCss;
	}
	
	// log relative functions
	function log(msg) {
		if(setting.showLog && window.console != null) {
			console.log(msg);
		}
	}
	function info(msg) {
		if(window.console != null) {
			console.info(msg);
		}
	}
	function warn(msg) {
		if(window.console != null) {
			console.warn(msg);
		}
	}
	function error(msg) {
		if(window.console != null) {
			console.error(msg);
		}
	}
	
	function constructor(caller, options) {
		// instance private methods
		// flag & variables
		var _this = null;                 // to remember current window instance
		var windowId = "window_" + (windowIndex++); // the window's id
		var minimized = false;            // a boolean flag to tell the window is minimized
		var maximized = false;            // a boolean flag to tell the window is maximized
		var selected = false;             // a boolean flag to tell the window is selected
		var redirectCheck = false;        // a boolean flag to control popup message while browser is going to leave this page
		var pos = new Object();           // to save cascade mode current position
		var wh = new Object();            // to save cascade mode current width & height
		var orgPos = new Object();        // to save position before minimize
		var orgWh = new Object();         // to save width & height before minimize
		var targetCssStyle = {};          // to save target css style json object
		var headerFuncPanel = null;       // header function bar element object
		var funcBarWidth = 0;             // the width of header function bar
		var miniStackIndex = -1;          // the index of window in minimized stack
		var animating = false;            // a boolean flag to indicate the window is doing animate
		var textPanelWidthOffset = 0;     // the width offset that title text panel should decrease
		
		// element
		var container = null;             // whole window container element
		var header = null;                // the header panel of window. it includes title text and buttons
		var frame = null;                 // the content panel of window. it could be a iframe or a div element, depending on which way you create it
		var footer = null;                // the footer panel of window. currently, it got nothing, but maybe a status bar or something will be added in the future 
		
		// the instance options
		var options = $.extend({
			icon: "auto",                 // [string:"auto"] a icon image url string. if this attribute is given, it will force to replace the original favicon of remote page on window.
			                              // or you can set it as null to hide icon.
			title: "",                    // [string:""] the title text of window
			url: "",                      // [string:""] the target url of iframe ready to load.
			content: "",                  // [html string, jquery object, element:""] this attribute only works when url is null. when passing a jquery object or a element, it will clone the original one to append.
			footerContent: "",            // [html string, jquery object, element:""] same as content attribute, but it's put on footer panel.
			containerClass: "",           // [string:""] container extra class
			headerClass: "",              // [string:""] header extra class
			frameClass: "",               // [string:""] frame extra class
			footerClass: "",              // [string:""] footer extra class
			selectedHeaderClass: "",      // [string:""] selected header extra class
			x: -1,                        // [number:-1] the x-axis value on screen(or caller element), if -1 means put on screen(or caller element) center
			y: -1,                        // [number:-1] the y-axis value on screen(or caller element), if -1 means put on screen(or caller element) center
			z: maxZIndex,                      // [number:2000] the css z-index value
			width: 400,                   // [number:400] window width
			height: 300,                  // [number:300] window height
			minWidth: 200,                // [number:200] the minimum width, if -1 means no checking
			minHeight: 150,               // [number:150] the minimum height, if -1 means no checking
			maxWidth: 800,                // [number:800] the maximum width, if -1 means no checking
			maxHeight: 600,               // [number:600] the maximum height, if -1 means no checking
			showModal: false,             // [boolean:false] to control show modal on background
			modalOpacity: 0.5,            // [number:0.5] the opacity of modal dialog
			showFooter: true,             // [boolean:true] to control show footer panel
			showRoundCorner: false,       // [boolean:true] to control display window as round corner
			closable: true,               // [boolean:true] to control window closable
			minimizable: true,            // [boolean:true] to control window minimizable
			maximizable: true,            // [boolean:true] to control window maximizable
			bookmarkable: true,           // [boolean:true] to control window with remote url could be bookmarked
			draggable: true,              // [boolean:true] to control window draggable
			resizable: true,              // [boolean:true] to control window resizable
			scrollable: true,             // [boolean:true] to control show scroll bar or not
			checkBoundary: false,         // [boolean:false] to check window dialog overflow html body or caller element
			withinBrowserWindow: false,   // [boolean:false] to limit window only can be dragged within browser window. this attribute only works when checkBoundary is true and caller is null. 
			custBtns: null,               // [json array:null] to describe the customized button display & callback function
			onOpen: null,                 // [function:null] a callback function while container is added into body
			onShow: null,                 // [function:null] a callback function while whole window display routine is finished
			onClose: null,                // [function:null] a callback function while user click close button
			onSelect: null,               // [function:null] a callback function while user select the window
			onUnselect: null,             // [function:null] a callback function while window unselected
			onDrag: null,                 // [function:null] a callback function while window is going to drag
			afterDrag: null,              // [function:null] a callback function after window dragged
			onResize: null,               // [function:null] a callback function while window is going to resize
			afterResize: null,            // [function:null] a callback function after window resized
			onMinimize: null,             // [function:null] a callback function while window is going to minimize
			afterMinimize: null,          // [function:null] a callback function after window minimized
			onMaximize: null,             // [function:null] a callback function while window is going to maximize
			afterMaximize: null,          // [function:null] a callback function after window maximized
			onCascade: null,              // [function:null] a callback function while window is going to cascade
			afterCascade: null,           // [function:null] a callback function after window cascaded
			onIframeStart: null,          // [function:null] a callback function while iframe ready to connect remoting url. this attribute only works while url attribute is given
			onIframeEnd: null,            // [function:null] a callback function while iframe load finished. this attribute only works while url attribute is given
			iframeRedirectCheckMsg: null, // [string:null] if null means no check, or pass a string to show warning message while iframe is going to redirect
			createRandomOffset: {x:0, y:0} // [json object:{x:0, y:0}] random the new created window position, it only works when options x,y value both are -1
		}, options);
		
		function initialize(instance) {
			_this = instance;
			
			if( options.showModal ) {
				showOverlay();
			}
			
			// build html
			var realCaller = caller != null? caller:$("body");
			var cornerClass = options.showRoundCorner? "ui-corner-all ":"";
			realCaller.append("<div id='"+windowId+"' class='window_panel "+cornerClass+options.containerClass+"'></div>");
			container = realCaller.children("div#"+windowId);
	
			// onOpen call back
			if( $.isFunction(options.onOpen) ) {
				options.onOpen(_this);
			}
			
			wh.w = options.width;
			wh.h = options.height;
			container.width(options.width);
			container.height(options.height);
			container.css("z-index", options.z);
			if( $.browser.msie ) { // To fix the right or bottom edge of window can't be trigger to resize while scrollbar appears on IE browser
				container.css({
					paddingRight: 1,
					paddingBottom: 1
				});
			}

			if( options.x >= 0 || options.y >= 0 ) {
				var scrollPos = getBrowserScrollXY();
				// set position x
				if( options.x >= 0 ) {
					var pLeft = 0;
					if( caller != null ) {
						var cpos = getParentPanelStartPos(caller);
						pLeft = options.x + cpos.left;
					} else {
						pLeft = options.x + scrollPos.left;
					}
					container.css("left", pLeft);
				} else { // put on center
					alignHorizontalCenter();
				}
	
				// set position y
				if( options.y >= 0 ) {
					var pTop = 0;
					if( caller != null ) {
						var cpos = getParentPanelStartPos(caller);
						pTop = options.y + cpos.top;
					} else {
						pTop = options.y + scrollPos.top;
					}
					container.css("top", pTop);
				} else { // put on middle
					alignVerticalCenter();
				}
			} else {
				alignCenter();
			}
			// feed x,y with real pixel value(not a percentage), to avoid "JUMPING" while window restore from minized status
			var currPos = container.position();
			container.css({
				left: currPos.left,
				top: currPos.top
			});
			
			// prepare favicon url
			if( options.icon == "auto" ) {
				options.icon = _prepareFaviconUrl();
			}
			var iconHtml = '';
			if( options.icon != null ) {
				iconHtml = "<img class='window_title_icon' src='"+options.icon+"' style='display:none;' onload='javascript:$.Window._iconOnLoad(this);'/>";
			}
	
			// build header html
			cornerClass = options.showRoundCorner? "ui-corner-top ":"";
			var headerHtml = "<div class='window_header window_header_normal ui-widget-header "+cornerClass+"no-resizable "+options.headerClass+"'>"+
				iconHtml+
				"<div class='window_title_text'>"+options.title+"</div>"+
				"<div class='window_function_bar'></div>"+
				"</div>";
			container.append(headerHtml);
			header = container.children("div.window_header");
			hideIcon();
			
			// bind double click event with doing maximize action
			if( options.maximizable ) {
				header.dblclick(function() {
					if( maximized ) {
						restore();
					} else {
						maximize();
					}
				});
			}
			
			headerFuncPanel = header.children("div.window_function_bar");
			// add close button
			if( options.closable ) {
				headerFuncPanel.append( "<div title='close window' class='closeImg window_icon_button no-draggable'></div>" );
				headerFuncPanel.children(".closeImg").click(function() {
					close();
				});
				funcBarWidth += ICON_OFFSET;
			}
	
			// add maximize button
			if( options.maximizable ) {
				headerFuncPanel.append( "<div title='maximize window' class='maximizeImg window_icon_button no-draggable'></div>" );
				headerFuncPanel.append( "<div title='cascade window' class='cascadeImg window_icon_button no-draggable' style='display:none;'></div>" );
				headerFuncPanel.children(".maximizeImg").click(function() {
					maximize();
				});
				headerFuncPanel.children(".cascadeImg").click(function() {
					restore();
				});
				funcBarWidth += ICON_OFFSET;
			}
	
			// add minimize button
			if( options.minimizable ) {
				headerFuncPanel.append( "<div title='minimize window' class='minimizeImg window_icon_button no-draggable'></div>" );
				headerFuncPanel.children(".minimizeImg").click(function() {
					minimize();
				});
				funcBarWidth += ICON_OFFSET;
			}
	
			// add bookmark button
			if( options.bookmarkable && options.url != null && $.trim(options.url) != "" ) {
				headerFuncPanel.append( "<div title='bookmark this' class='bookmarkImg window_icon_button no-draggable'></div>" );
				headerFuncPanel.children(".bookmarkImg").click(function() {
					doBookmark(options.title, options.url);
				});
				funcBarWidth += ICON_OFFSET;
			}
	
			// add customized buttons
			addCustomizedButtns(headerFuncPanel);
			
			// make buttons don't pass dblclick event to header panel 
			$(".window_icon_button").dblclick(function() {
				return false;
			});
	
			// set text & function bar width
			adjustHeaderTextPanelWidth();
			headerFuncPanel.width( funcBarWidth );
	
			// build iframe html
			var frameHeight = getFrameHeight(wh.h);
			if( options.url != null && $.trim(options.url) != "" ) {
				// iframe starting call back
				if( $.isFunction(options.onIframeStart) ) {
					log("start connecting iframe: "+options.url);
					options.onIframeStart(_this, options.url);
				}
	
				// add iframe redirect checking
				if( options.iframeRedirectCheckMsg ) {
					redirectCheck = true;
					window.onbeforeunload = function() {
						if( redirectCheck ) {
							var msg = options.iframeRedirectCheckMsg.replace("{url}", options.url);
							return msg;
						}
					}
				}
	
				// show loading image
				container.append("<div class='frame_loading'>Loading...</div>");
				var loading = container.children(".frame_loading");
				loading.css("marginLeft",	'-' + (loading.outerWidth() / 2) - 20 + 'px');
				loading.click(function() {
					loading.remove();
				});
	
				// append iframe html
				var scrollingHtml = options.scrollable? "yes":"no";
				container.append("<iframe style='display:none;' class='window_frame ui-widget-content no-draggable no-resizable "+options.frameClass+"' scrolling='"+scrollingHtml+"' src='"+options.url+"' width='100%' height='"+frameHeight+"px' frameborder='0'></iframe>");
				frame = container.children(".window_frame");
	
				// iframe load finished call back
				frame.ready(function() {
					frame.show();
				});
	
				frame.load(function() {
					redirectCheck = false;
					loading.remove();
					log("load iframe finished: "+options.url);
					if( $.isFunction(options.onIframeEnd) ) {
						options.onIframeEnd(_this, options.url);
					}
				});
			} else {
				container.append("<div class='window_frame ui-widget-content no-draggable no-resizable "+options.frameClass+"' style='width:100%; height:"+frameHeight+"px;'></div>");
				frame = container.children(".window_frame");
				if( options.content != null ) {
					setContent(options.content);
					frame.children().show();
				}
				frame.css({
					overflow: options.scrollable? "auto":"hidden"
				});
			}
	
			// build footer html
			if( options.showFooter ) {
				cornerClass = options.showRoundCorner? "ui-corner-bottom ":"";
				container.append("<div class='window_footer ui-widget-content "+cornerClass+"no-draggable no-resizable "+options.footerClass+"'><div></div></div>");
				footer = container.children("div.window_footer");
				if( options.footerContent != null ) {
					setFooterContent(options.footerContent);
					footer.children("div").children().show();
				}
			} else {
				cornerClass = options.showRoundCorner? "ui-corner-bottom ":"";
				frame.addClass(cornerClass);
			}
	
			// bind container handle mousedown event
			container.mousedown(function() {
				selectWindow(caller, _this);
			});
	
			// make window draggable
			if( options.draggable ) {
				container.draggable({
					cancel: ".no-draggable",
					start: function() {
						log( "drag start" );
						if( minimized || maximized ) { // if window is minimized or maximized, reset the css style
							container.css("position", "fixed");
							container.css(targetCssStyle);
						}
						showOverlay();
						hideContent();
						// callback
						if( options.onDrag ) {
							options.onDrag(_this);
						}
					},
					stop: function() {
						log( "drag stop" );
						if( minimized || maximized ) { // if window is minimized or maximized, reset the css style
							container.css("position", "fixed");
							container.css(targetCssStyle);
						}
						hideOverlay();
						showContent();
						// callback
						if( options.afterDrag ) {
							options.afterDrag(_this);
						}
					}
				});
				// set boundary if got opotions
				if( options.checkBoundary ) {
					if( options.withinBrowserWindow && caller == null ) {
						container.draggable('option', 'containment', 'window');
					} else {
						container.draggable('option', 'containment', [0,30,1000000,1000000]);
					}
				}
			}
	
			// make window resizable
			if( options.resizable ) {
				container.resizable({
					cancel: ".no-resizable",
					alsoResize: frame,
					start: function() { // this will be triggered when window is going to drag in minimized or maximized mode
						log( "resize start" );
						if( minimized || maximized ) { // if window is minimized or maximized, reset the css style
							return false;
						}
						showOverlay();
						hideContent();
						// callback
						if( options.onResize ) {
							options.onResize(_this);
						}
					},
					stop: function() {
						log( "resize stop" );
						if( minimized || maximized ) { // if window is minimized or maximized, reset the css style
							return false;
						}
						hideOverlay();
						adjustHeaderTextPanelWidth();
						showContent();
						// callback
						if( options.afterResize ) {
							options.afterResize(_this);
						}
					}
				});
				// set boundary if got opotions
				if( options.checkBoundary ) {
					// this got bug, so mark it temporarily
					//container.resizable('option', 'containment', "parent");
					//if( options.withinBrowserWindow && caller == null ) {
						//container.resizable('option', 'containment', "window");
					//}
				}
	
				// set resize min, max width & height
				if( options.maxWidth >= 0 ) {
					container.resizable('option', 'maxWidth', options.maxWidth);
				}
				if( options.maxHeight >= 0 ) {
					container.resizable('option', 'maxHeight', options.maxHeight);
				}
				if( options.minWidth >= 0 ) {
					container.resizable('option', 'minWidth', options.minWidth);
				}
				if( options.minHeight >= 0 ) {
					container.resizable('option', 'minHeight', options.minHeight);
				}
			}

			// onShow call back
			if( $.isFunction(options.onShow) ) {
				options.onShow(_this);
			}
		}
		
		function getDomain(url) {
			var tmp = url.match(/:\/\/(.[^/]+)/);
			if( tmp != null && tmp.length >= 2 ) {
				return tmp[1];
			} else {
				return null;
			}
		}
		
		// insert icon element html, this should be call only when the icon doesn't exist
		function _addIcon() {
			if( options.icon != null && options.icon != '' ) {
				var html = "<img class='window_title_icon' src='"+options.icon+"' style='display:none;' onload='javascript:$.Window._iconOnLoad(this);'/>";
				header.prepend(html);
			}
		}
		
		function setIcon(iconUrl) {
			options.icon = iconUrl;
			
			// prepare favicon url
			if( options.icon == "auto" ) {
				options.icon = _prepareFaviconUrl();
			}
			
			if( options.icon != null ) {
				var icon = header.children('.window_title_icon');
				// check icon element existed?
				if( icon.get(0) != null ) { // hide icon and set image source url
					hideIcon();
					icon.attr('src', options.icon);					
				} else { // icon doesn't exist, insert icon element html
					_addIcon();
				}
			}
		}
		
		// show icon element
		function showIcon() {
			var icon = header.children('.window_title_icon');
			if( icon.get(0) != null ) {
				icon.show();
				
				// re-arrange title text area 
				var txt = header.children('.window_title_text');
				txt.css('margin-left', '0');
				textPanelWidthOffset = 20;
				adjustHeaderTextPanelWidth();
			}
		}
		
		// hide icon element
		function hideIcon() {
			var icon = header.children('.window_title_icon');
			if( icon.get(0) != null ) {
				icon.hide();
				
				// re-arrange title text area 
				var txt = header.children('.window_title_text');
				txt.css('margin-left', '-20px');
				textPanelWidthOffset = 0;
				adjustHeaderTextPanelWidth();
			}
		}
		
		// set window title
		function setTitle(title) {
			options.title = title;
			header.children(".window_title_text").text(title);
			if( minimized ) {
				_transformTitleText();
			}
		}
		
		// get window title
		function getTitle() {
			return options.title;
		}
		
		function setUrl(url) {
			options.url = url;
			frame.attr("src", url);
			
			if( options.icon != null ) {
				setIcon('auto');
			}
		}
		
		function _prepareFaviconUrl() {
			if( options.url != null && $.trim(options.url) != "" ) {
				var domain = getDomain(options.url);
				if( domain != null ) {
					return 'http://'+domain+'/favicon.ico';
				}
			}
			return null;
		}
		
		function getUrl() {
			return options.url;
		}
		
		function setContent(content) {
			options.content = content;
			if( typeof content == 'object' ) {
				content = $(content).clone(true);
			} else if( typeof content == 'string' ) {
				// using original content
			}
			frame.empty();
			frame.append(content);
		}
		
		function getContent() {
			return frame.html();
		}
		
		function setFooterContent(content) {
			if( options.showFooter ) {
				options.footerContent = content;
				if( typeof content == 'object' ) {
					content = $(content).clone(true);
				} else if( typeof content == 'string' ) {
					// using original content
				}
				footer.children("div").empty();
				footer.children("div").append(content);
			}
		}
		
		function getFooterContent() {
			return footer.children("div").html();
		}
		
		// popup a overlay panel block whole screen while window dragging or resizing
		// to avoid lost event while mouse cursor over iframe region. [ISU_003]
		function showOverlay() {
			var overlay = $("#window_overlay");
			if( overlay.get(0) == null ) {
				$("body").append("<div id='window_overlay'>&nbsp;</div>");
				overlay = $("#window_overlay");
				overlay.css('z-index', maxZIndex+1);
			}
			
			if( options.showModal ) {
				overlay.css({
					opacity: options.modalOpacity
				});
			} else {
				overlay.css({
					opacity: 0
				});
			}
			overlay.show();
		}
		
		function hideOverlay(bForce) {
			if( options.showModal == false || bForce ) {
				$("#window_overlay").hide();
			}
		}
		
		function transferToFixed() {
			var currPos = container.offset();
			var scrollPos = getBrowserScrollXY();
			container.css({
				position: "fixed", // this will cause IE brwoser UI error, See ISU_004
				left: currPos.left - scrollPos.left,
				top: currPos.top - scrollPos.top
			});
		}
	
		function transferToAbsolute() {
			var currPos = container.offset();
			container.css({
				position: "absolute",
				left: currPos.left,
				top: currPos.top
			});
		}
	
		function addCustomizedButtns(headerFuncPanel) {
			if( options.custBtns != null && typeof options.custBtns == 'object' ) {
				for( var i=0; i<options.custBtns.length; i++ ) {
					var btnData = options.custBtns[i];
					if( btnData != null && typeof btnData == 'object' ) {
						if( btnData.id != null && btnData.callback != null ) { // it's a JSON object
							var id = btnData.id != null? btnData.id:"";
							var clazz = btnData.clazz != null? btnData.clazz:"";
							var title = btnData.title != null? btnData.title:"";
							var style = btnData.style != null? btnData.style:"";
							var image = btnData.image != null? btnData.image:"";
							var callback = btnData.callback != null? btnData.callback:"";
							if( btnData.image != null && btnData.image != "" ) {
								headerFuncPanel.append( "<img id='"+id+"' src='"+image+"' title='"+title+"' class='"+clazz+" window_icon_button no-draggable' style='"+style+"'/>" );
							} else {
								headerFuncPanel.append( "<div id='"+id+"' src='"+image+"' title='"+title+"' class='"+clazz+" window_icon_button no-draggable' style='"+style+"'></div>" );
							}
							var btn = headerFuncPanel.children("[id="+id+"]");
							btn.get(0).clickCb = callback;
							if( $.isFunction(callback)) {
								btn.click(function() {
									this.clickCb($(this), _this);
								});
							}
						} else { // it's a html element(or wrapped with jQuery)
							var btn = $(btnData).clone(true);
							btn.addClass("window_icon_button no-draggable cust_button");
							headerFuncPanel.append( btn );
							btn.show();
						}
					}
					funcBarWidth += ICON_OFFSET;
				}
			}
		}
		
		function _adjustMinimizedPos(bImmediate, callback) {
			animating = true;
			targetCssStyle = getCssStyleByDock(caller, miniStackIndex);
			if( bImmediate ) {
				container.css(targetCssStyle);
				animating = false;
				if( $.isFunction(callback) ) {
					callback();
				}
			} else {
				container.animate(targetCssStyle, setting.animationSpeed, 'swing', function() {
					animating = false;
					if( $.isFunction(callback) ) {
						callback();
					}
				});
			}
		}
		
		function adjustHeaderTextPanelWidth() {
			header.children("div.window_title_text").width( header.width() - funcBarWidth - textPanelWidthOffset );
		}
	
		function adjustFrameWH() {
			var width = container.width();
			var height = container.height();
			var frameHeight = getFrameHeight(height);
			frame.width( width );
			frame.height( frameHeight );
		}
	
		function doBookmark(title, url) {
			if ( $.browser.mozilla && window.sidebar ) { // Mozilla Firefox Bookmark
				window.sidebar.addPanel(title, url, "");
			} else if( $.browser.msie && window.external ) { // IE Favorite
				window.external.AddFavorite( url, title);
			} else if( ua.indexOf("chrome") >= 0 ) { // Chrome
				alert("Sorry! Chrome doesn't support bookmark function currently.");
				//alert("Press [Ctrl + D] to bookmark in Chrome");
			} else if($.browser.safari || ua.indexOf("safari") >= 0 ) { // Safari
				alert("Sorry! Safari doesn't support bookmark function currently.");
				//alert("Press [Ctrl + D] to bookmark in Safari");
			} else if($.browser.opera || ua.indexOf("opera") >= 0 ) { // Opera Hotlist
				alert("Sorry! Opera doesn't support bookmark function currently.");
				//alert("Press [Ctrl + D] to bookmark in Opera");
			}
		}
	
		function hideContent() {
			var bgColor = frame.css("backgroundColor");
			//log("hideContent: "+bgColor);
			if( bgColor != null && bgColor != "transparent" && bgColor != "rgba(0, 0, 0, 0)" ) {
				container.css("backgroundColor", bgColor);
			}
			frame.hide();
			if( options.showFooter ) {
				footer.hide();
			}
			container.css("opacity", OPACITY_MINIMIZED);
		}
	
		function showContent() {
			//log("showContent");
			frame.show();
			if( options.showFooter ) {
				footer.show();
			}
			container.css("opacity", 1);
		}
	
		function getFrameHeight(windowHeight) {
			var footerHeight = options.showFooter? 16:0;
			return windowHeight - 20 - footerHeight - 4 - 1; // minus header & footer & iframe's padding height & border
		}
	
		// modify title text as vertical presentation
		function _transformTitleText() {
			if( setting.dock == 'top' || setting.dock == 'bottom' ) {
				return;
			}
			
			var textBlock = header.children("div.window_title_text");
			// check icon visible
			var icon = header.children('.window_title_icon');
			if( icon.is(':visible') ) {
				textBlock.addClass('window_title_text_vertical_with_icon');
			} else {
				textBlock.addClass('window_title_text_vertical');
			}
			
			//var text = textBlock.text();
			var text = options.title;
			var buf = "";
			
			for( var i=0; i<text.length; i++ ) {
				var c = text.charAt(i);
				if( c == "-" || c == "_" ) {
					c = "|";
				}
				if( c == " " ) {
					c = "<div style='height:5px; line-height:5px;'>&nbsp;</div>";
					buf += c;
				} else {
					buf += c+"<br>";
				}
			}
			textBlock.html(buf);
		}
	
		function restoreTitleText() {
			var textBlock = header.children("div.window_title_text");
			textBlock.removeClass('window_title_text_vertical');
			textBlock.removeClass('window_title_text_vertical_with_icon');
			textBlock.text(options.title);
		}
		
		// public
		function getCaller() {
			return caller;
		}
	
		function getContainer() {
			return container;
		}
	
		function getHeader() {
			return header;
		}
	
		function getFrame() {
			return frame;
		}
	
		function getFooter() {
			return footer;
		}
		
		function getTargetCssStyle() {
			return targetCssStyle;
		}
		
		function alignCenter() {
			var pLeft = 0, pTop = 0;
			if( caller != null ) {
				var cpos = getParentPanelStartPos(caller);
				pLeft = cpos.left + (caller.width() - container.width()) / 2;
				pTop = cpos.top + (caller.height() - container.height()) / 2;
			} else {			
				var scrollPos = getBrowserScrollXY();
				var screenWH = getBrowserScreenWH();
				pLeft = scrollPos.left + (screenWH.width - container.width()) / 2;
				pTop = scrollPos.top + (screenWH.height - container.height()) / 2;
			};
			
			// random new created window position
			if( options.createRandomOffset.x > 0 ) {
				pLeft += ((Math.random() - 0.5) * options.createRandomOffset.x); 
			}
			if( options.createRandomOffset.y > 0 ) {
				pTop += ((Math.random() - 0.5) * options.createRandomOffset.y);					
			}
			container.css({
				left: pLeft,
				top: pTop
			});
		}
	
		function alignHorizontalCenter() {
			var pLeft = 0;
			if( caller != null ) {
				pLeft = getParentPanelStartPos(caller).left + (caller.width() - container.width()) / 2;
			} else {
				var scrollPos = getBrowserScrollXY();
				var screenWH = getBrowserScreenWH();
				pLeft = scrollPos.left + (screenWH.width - container.width()) / 2;
			}
			container.css({
				left: pLeft
			});
		}
	
		function alignVerticalCenter() {
			var pTop = 0;
			if( caller != null ) {
				pTop = getParentPanelStartPos(caller).top + (caller.height() - container.height()) / 2;
			} else {
				var scrollPos = getBrowserScrollXY();
				var screenWH = getBrowserScreenWH();
				pTop = scrollPos.top + (screenWH.height - container.height()) / 2;
			}
			container.css({
				top: pTop
			});
		}
		
		function select() {
			selected = true;
			if( maximized == false ) {
                maxZIndex++;
				container.css('z-index', maxZIndex);
				if( options.selectedHeaderClass ) {
					header.addClass(options.selectedHeaderClass); // add selected header class
				}
			}
			if( $.isFunction(options.onSelect) ) {
				options.onSelect();
			}
		}
	
		function unselect() {
			selected = false;
			if( maximized == false ) {
				//container.css('z-index', options.z);
				if( options.selectedHeaderClass ) {
					header.removeClass(options.selectedHeaderClass);
				}
			}
			if( $.isFunction(options.onUnselect) ) {
				options.onUnselect();
			}
		}
		
		/**
		 * @param x - the absolute x-axis value on document or shift distance, in pixels
		 * @param y - the absolute y-axis value on document or shift distance, in pixels
		 * @param bShift - a boolean flag to decide to shift the window position with x,y
		 */
		function move(x, y, bShift) {
			if( !maximized && !minimized ) {
				var styleObj = {};
				if( typeof x == 'number' ) {
					if( bShift ) {
						var currPos = container.offset();
						x += currPos.left;
					}
					styleObj.left = x;
				}
				if( typeof y == 'number' ) {
					styleObj.top = y;
					if( bShift ) {
						var currPos = container.offset();
						y += currPos.top;
					}
					styleObj.top = y;
				}
				container.css(styleObj);
			}
		}
		
		function resize(w, h) {
			if( !maximized && !minimized ) {
				var styleObj = {};
				if( w > 0 ) {
					styleObj.width = w;
				}
				if( h > 0 ) {
					styleObj.height = h;
				}
				container.css(styleObj);
				adjustHeaderTextPanelWidth();
		        adjustFrameWH();
			}
		}
	
		function maximize(bImmediately, bNoSaveDisplay) {
			if( !$.browser.msie && caller == null ) { // in IE, must do hide scrollbar routine after animation finished
				hideBrowserScrollbar();
			}
			maximized = true;
			container.draggable( 'disable' );
			container.resizable( 'disable' );
	
			// save current display
			if( bNoSaveDisplay != true ) {
				pos.left = container.css("left");
				pos.top = container.css("top");
				wh.w = container.width();
				wh.h = container.height();
			}
			// must add this, or it will get a bug when user mouse down the border of window panel if it is resized.
			container.addClass('no-resizable');
			
			var scrollPos = getBrowserScrollXY();
			var screenWH = getBrowserScreenWH();
			if( caller != null ) {
				var cpos = getParentPanelStartPos(caller);
				targetCssStyle = {
					left: cpos.left,
					top: cpos.top,
					width: caller.width(),
					height: caller.height(),
					opacity: 1
				};
			} else {
				targetCssStyle = {
					left: scrollPos.left,
					top: scrollPos.top,
					width: screenWH.width,
					height: screenWH.height,
					opacity: 1
				};
			}
	
			if( bImmediately ) {
				container.css(targetCssStyle);
				adjustHeaderTextPanelWidth();
				adjustFrameWH();
				header.removeClass('window_header_normal');
				header.addClass('window_header_maximize');
				// switch maximize, cascade button
				headerFuncPanel.children(".maximizeImg").hide();
				headerFuncPanel.children(".cascadeImg").show();
			} else {
				hideContent();
				container.animate(targetCssStyle, setting.animationSpeed, 'swing', function() {
					if( $.browser.msie && caller == null ) { // in IE, must do hide scrollbar routine after animation finished
						hideBrowserScrollbar();
					}
					showContent();
					adjustHeaderTextPanelWidth();
					adjustFrameWH();
					header.removeClass('window_header_normal');
					header.addClass('window_header_maximize');
					// switch maximize, cascade button
					headerFuncPanel.children(".maximizeImg").hide();
					headerFuncPanel.children(".cascadeImg").show();
					
					// after callback
					if( $.isFunction(options.afterMaximize) ) {
						options.afterMaximize(_this);
					}
				});
				container.css('z-index', options.z+3);
			}
			
			// before callback
			if( $.isFunction(options.onMaximize) ) {
				options.onMaximize(_this);
			}
		}
	
		function minimize() {
			hideOverlay(true);
			showBrowserScrollbar();
			minimized = true;
			container.draggable( 'disable' );
            console.log("disabling ", container);
			//container.resizable( 'disable' );
			
			// save current display
			orgPos.left = container.css("left");
			orgPos.top = container.css("top");
			orgWh.w = container.width();
			orgWh.h = container.height();

			miniStackIndex = getMinWindowLength(caller);
			targetCssStyle = {
				opacity: OPACITY_MINIMIZED
			};
			// must add this, or it will get a bug when user mouse down the border of window panel if it is resized.
			container.addClass('no-resizable');
			
			if( caller == null && setting.dockArea == null ) {
				transferToFixed(); // transfer position to fixed first
			}
			headerFuncPanel.hide();
			hideContent();
			
			// check minimized windows' size
			checkMinWindowSize(caller, true);
			_adjustMinimizedPos(false, function() {
				container.css('z-index', options.z);
				header.children("div.window_title_text").width( "96%" );
				header.attr("title", options.title);
				header.removeClass('window_header_normal');
				header.removeClass('window_header_maximize');
				header.addClass('window_header_minimize');
				if( setting.dock == 'left' || setting.dock == 'right' ) {
					header.addClass('window_header_minimize_vertical');
				}
				
				if( options.showRoundCorner ) {
					header.removeClass('ui-corner-top');
					header.addClass('ui-corner-all');
				}
				_transformTitleText();
	
				// bind header click event
				header.click(function() {
					if( !animating ) {
						restore();
					}
				});
				
				// after callback
				if( $.isFunction(options.afterMinimize) ) {
					options.afterMinimize(_this);
				}
			});
			container.mouseover(function() {
				$(this).css("opacity", 1);
			});
			container.mouseout(function() {
				$(this).css("opacity", OPACITY_MINIMIZED);
			});
			
			// before callback
			if( $.isFunction(options.onMinimize) ) {
				options.onMinimize(_this);
			}
			
			// push into minimized window storage
			pushMinWindow(caller, _this);
		}
	
		function restore() {
			if( options.showModal ) {
				showOverlay();
			}
			var rpos = null;
			var rwh = null;
			var zIndex = options.z+2;
			if( minimized ) { // from minimized status
				rpos = orgPos;
				rwh = orgWh;
				if( caller == null ) {
					transferToAbsolute(); // transfer position to fixed first
				}
				restoreTitleText();
				header.removeAttr("title");
				header.removeClass('window_header_minimize');
				header.removeClass('window_header_minimize_vertical');
				if( maximized ) { // minimized -> maximized
					header.addClass('window_header_maximize');
					if( caller != null ) {
						rpos = getParentPanelStartPos(caller);
					} else {
						var scrollPos = getBrowserScrollXY();
						rpos = {
							left: scrollPos.left,
							top: scrollPos.top
						};
					}
					zIndex = options.z+3;
					container.css('z-index', zIndex); // change z-index before animating
				} else { // minimized -> cascade
					header.addClass('window_header_normal');
					// must add this, or it will get a bug when user mouse down the border of window panel if it is resized.
					container.removeClass('no-resizable');
				}
			} else if( maximized ) { // maximized -> cascade
				maximized = false;
				rpos = pos;
				rwh = wh;
				header.removeClass('window_header_maximize');
				header.addClass('window_header_normal');
				// must add this, or it will get a bug when user mouse down the border of window panel if it is resized.
				container.removeClass('no-resizable');
			}

			if( options.showRoundCorner ) {
				header.removeClass('ui-corner-all');
				header.addClass('ui-corner-top');
			}
			
			// unbind event
			container.unbind("mouseover");
			container.unbind("mouseout");

			targetCssStyle = {
				left: rpos.left,
				top: rpos.top,
				width: rwh.w,
				height: rwh.h,
				opacity: 1
			};
			
			hideContent();
			container.animate(targetCssStyle, setting.animationSpeed, 'swing', function() {
				container.css('z-index', zIndex);
				showContent();
				header.unbind('click');
				adjustHeaderTextPanelWidth();
				adjustFrameWH();
	
				// switch maximize, cacade icon
				if( maximized ) {
					if( caller == null ) {
						hideBrowserScrollbar();
					}
					headerFuncPanel.children(".maximizeImg").hide();
					headerFuncPanel.children(".cascadeImg").show();
				} else {
					showBrowserScrollbar();
					container.draggable( 'enable' );
					container.resizable( 'enable' );
					headerFuncPanel.children(".maximizeImg").show();
					headerFuncPanel.children(".cascadeImg").hide();
				}
				headerFuncPanel.show();
				
				// pop from minimized window storage
				if( minimized ) {
					minimized = false;
					popMinWindow(caller, _this);
					checkMinWindowSize(caller, false);
					adjustAllMinWindows(caller); // adjust minimized window position
				}
				
				// after callback
				if( $.isFunction(options.afterCascade) ) {
					options.afterCascade(_this);
				}
			});
			
			// before callback
			if( $.isFunction(options.onCascade) ) {
				options.onCascade(_this);
			}
		}
		
		function close(quiet) {
			// do callback
			if( !quiet && $.isFunction(options.onClose) ) {
				options.onClose(_this);
			}
			destroy();
		}
	
		function destroy() {
			redirectCheck = false;
			if( maximized ) {
				showBrowserScrollbar();
			}
			popWindow(_this);
			container.remove();
			hideOverlay(true);
		}
		
		function show() {
			container.show();
		}
		
		function hide() {
			container.hide();
		}
		
		function _decreaseMiniIndex() {
			miniStackIndex--;
		}
	
		return { // instance public methods
			initialize: initialize,
			getTargetCssStyle: getTargetCssStyle,         // get the css ready to change
			getWindowId: function() {                     // get window id
				return windowId;
			},
			getCaller: getCaller,                         // get window container's parent panel, it's a jQuery object
			getContainer: getContainer,                   // get window container panel, it's a jQuery object
			getHeader: getHeader,                         // get window header panel,it's  a jQuery object
			getFrame: getFrame,                           // get window frame panel, it's a jQuery object
			getFooter: getFooter,                         // get window footer panel, it's a jQuery object
			alignCenter: alignCenter,                     // set current window as screen center
			alignHorizontalCenter: alignHorizontalCenter, // set current window as horizontal center
			alignVerticalCenter: alignVerticalCenter,     // set current window as vertical center
			select: select,                               // select current window, it will increase the original z-index value with 2
			unselect: unselect,                           // unselect current window, it will set the z-index as original options.z
			move: move,                                   // move current window to target position or shift it by passed distance
			resize: resize,                               // resize current window to target width/height
			maximize: maximize,                           // maximize current window
			minimize: minimize,                           // minimize current window
			restore: restore,                             // restore current window, it could be maximized or cascade status
			close: close,                                 // close current window. parameter: quiet - [boolean] to decide doing callback or not
			hide: hide,                                   // hide current window.
			show: show,                                   // show current window.
			setTitle: setTitle,                           // change window title. parameter: title - [string] window title text
			setUrl: setUrl,                               // change iframe url. parameter: url - [string] iframe url
			setContent: setContent,                       // change frame content. parameter: content - [html string, jquery object, element] the content of frame
			setFooterContent: setFooterContent,           // change footer content. parameter: content - [html string, jquery object, element] the content of footer
			getTitle: getTitle,                           // get window title text
			getUrl: getUrl,                               // get url string
			getContent: getContent,                       // get frame html content
			getFooterContent: getFooterContent,           // get footer html content
			isMaximized: function() {                     // get window maximized status
				return maximized;
			},
			isMinimized: function() {                     // get window minmized status
				return minimized;
			},
			isSelected: function() {                      // get window selected status
				return selected;
			},
			setIcon: setIcon,                             // set window icon
			showIcon: showIcon,                           // show window icon
			hideIcon: hideIcon,                           // hide window icon
			
			// for plugin private use
			_decreaseMiniIndex: _decreaseMiniIndex,
			_adjustMinimizedPos: _adjustMinimizedPos,
			_setOrgWH: function(wh) {
				orgWh.w = wh.width;
				orgWh.h = wh.height;
			},
			_transformTitleText: _transformTitleText,
			toString: function() {
				return '[Window] id='+windowId+', title='+options.title;
			}
		};
	} // constructor end
	
	return { // static public methods
		getInstance: function(caller, options) { // create new window instance
			var instance = constructor(caller, options);
			instance.initialize(instance);
			selectWindow(caller, instance); // set new created window instance as selected
			pushWindow(instance);
			if( caller != null ) {
				if( caller.get(0)._minWinData == null ) {
					// create the minimized window relative data
					caller.get(0)._minWinData = {
						long: setting.minWinLong,
						storage: []
					};
				}
				parentCallers.push(caller);
			}
			
			// static initialzation
			if( !initialized ) {
				// handle window resize event
				$(window).resize(function() {
					if( resizeTimer != null ) {
						clearTimeout(resizeTimer);
					}
					resizeTimer = window.setTimeout(function() {
						var screenWH = getBrowserScreenWH();
						for( var i=0, len=windowStorage.length; i<len; i++ ) {
							var wnd = windowStorage[i];
							if( wnd.isMaximized() ) {
								if( wnd.isMinimized() ) {
									// reset the restore width/height
									wnd._setOrgWH(screenWH);
								} else {
									wnd.maximize(true, true);
								}
							}
							
							if( wnd.isMinimized() ) {
								wnd._adjustMinimizedPos(true);
							}
						}
					}, RESIZE_EVENT_DELAY);
				});
				initialized = true;
			}
			
			return instance;
		},
		getVersion: function() { // get current version of plugin
			return VERSION;
		},
		prepare: function(custSetting) { // initialize with customerized static setting attributes
			$.extend(setting, custSetting);
			minWinData.long = setting.minWinLong;
			
			// check dock area attribute
			if( setting.dockArea != null ) {
				var dArea = $(setting.dockArea);
				if( dArea.get(0)._minWinData == null ) {
					// create the minimized window relative data
					dArea.get(0)._minWinData = {
						long: setting.minWinLong,
						storage: []
					};
				}
			}
		},
		closeAll: function(quiet) { // close all created windows. it got a parameter - quiet, a boolean flag to decide doing callback or not
			var count = windowStorage.length;
			for( var i=0; i<count; i++ ) {
				var wnd = windowStorage[0];
				wnd.close(quiet);
			}
			windowStorage = [];
			// reset minimized window data object
			minWinData.storage = [];
			minWinData.long = setting.minWinLong;
			for( var i=0; i<parentCallers.length; i++ ) {
				var mwdata = parentCallers[i].get(0)._minWinData;
				mwdata.storage = [];
				mwdata.long = setting.minWinLong;
			}
		},
		hideAll: function() { // hide all created windows
			for( var i=0, len=windowStorage.length; i<len; i++ ) {
				windowStorage[i].getContainer().hide();
			}
		},
		showAll: function() { // show all created windows
			for( var i=0, len=windowStorage.length; i<len; i++ ) {
				windowStorage[i].getContainer().show();
			}
		},
		getAll: function() { // return all created windows instance
			return windowStorage;
		},
		getWindow: getWindow, // get the window instance by passed window id
		getSelectedWindow: function() { // get the selected window instance
			for( var i=0, len=windowStorage.length; i<len; i++ ) {
				var wnd = windowStorage[i];
				if( wnd.isSelected() ) {
					return wnd;
				}
			}
		},
		_iconOnLoad: function(element) {
			var windowId = $(element).parent().parent().attr('id');
			log('_iconOnLoad: '+windowId);
			if( windowId != null ) {
				var wnd = $.Window.getWindow(windowId);
				if( wnd != null ) {
					wnd.showIcon();
				}
			} else {
				warn('[_iconOnLoad] lost window id!!!');
			}
		}
	}
})();

// alias methods
$.window.getVersion = $.Window.getVersion;
$.window.prepare = $.Window.prepare;
$.window.closeAll = $.Window.closeAll;
$.window.hideAll = $.Window.hideAll;
$.window.showAll = $.Window.showAll;
$.window.getAll = $.Window.getAll;
$.window.getWindow = $.Window.getWindow;
$.window.getSelectedWindow = $.Window.getSelectedWindow;
