(function () {
    'use strict';

    angular
        .module('yamcs.uss')
        .factory('ussService', ussService);

    /* @ngInject */
    function ussService() {

        return {
            drawAndConnectDisplay: drawAndConnectDisplay,
            updateWidget: updateWidget
        };

        function drawAndConnectDisplay(data, targetDiv, tmService, doneFunction) {
            $(targetDiv).svg({onLoad: function () {
                var display = new USS.Display(targetDiv);
                display.parseAndDraw(data);
                tmService.subscribeParameters(display.parameters);
                tmService.subscribeComputations(display.parameters);
                if (doneFunction) doneFunction(display);
            }});
        }

        function updateWidget(db, para) {
            var widget=db.widget;
            switch (db.dynamicProperty) {
                case USS.dp_VALUE:
                    if (widget.updateValue === undefined) {
                        //console.log('no updateValue for ', widget);
                        return;
                    }
                    widget.updateValue(para, db.usingRaw);
                    break;
                case USS.dp_X:
                    widget.updatePosition(para, 'x', db.usingRaw);
                    break;
                case USS.dp_Y:
                    widget.updatePosition(para, 'y', db.usingRaw);
                    break;
                case USS.dp_FILL_COLOR:
                    widget.updateFillColor(para, db.usingRaw);
                    break;
                default:
                    console.log('TODO update dynamic property: ', db.dynamicProperty);
            }
        }
    }
})();
