(function() {
    'use strict';

    angular
        .module('yamcs.alarms')
        .directive('alarmBlock', alarmBlock);

    /* @ngInject */
    function alarmBlock() {
        return {
            restrict: 'E',
            //replace: true,
            link: function (scope, element, attrs) {
                scope.toggleable = (attrs.hasOwnProperty('toggleable'));
            },
            scope: {
                alarm: '=',
                openAcknowledge: '&'
            },
            templateUrl: '/_static/_site/alarms/directives/alarm-block.directive.html'
        };
    }
})();
