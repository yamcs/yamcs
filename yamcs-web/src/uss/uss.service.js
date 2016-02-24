(function () {
    'use strict';

    angular
        .module('yamcs.uss')
        .factory('ussService', ussService);

    /* @ngInject */
    function ussService() {

        return {
            drawDisplay: drawDisplay
        };

        function drawDisplay(sourceCode, targetDiv, doneFunction) {
            $(targetDiv).svg({onLoad: function () {
                var display = new USS.Display(targetDiv);
                display.parseAndDraw(sourceCode);
                if (doneFunction) doneFunction(display);
            }});
        }
    }
})();
