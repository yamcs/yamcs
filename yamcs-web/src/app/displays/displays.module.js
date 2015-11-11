(function() {
    'use strict';

    angular
        .module('app.displays', ['app.tm'])
        .directive('displaysPane', displaysPane)
        .directive('embeddedDisplay', embeddedDisplay)
        .run(appRun);

    /* @ngInject */
    function displaysPane() {
        return {
            restrict: 'E',
            transclude: true,
            scope: { activePane: '@', title: '@' },
            templateUrl: '/_static/app/displays/displays.template.html'
        };
    }

    /* @ngInject */
    function embeddedDisplay(displaysService, tmService, $rootScope) {
        return {
            restrict: 'A',
            scope: { ref: '@' },
            link: function(scope, elem, attrs) {
                displaysService.getDisplay(attrs['ref']).then(function(rawDisplay) {
                    USS.drawAndConnectDisplay(rawDisplay, elem, tmService, function(display) {

                        // 'Leak' canvas color
                        // This should not be done here. But I'm not yet fully understanding angular
                        // intricacities when it comes to scopes, directives and DOM manipulations.
                        //elem.parents('.main').css('background-color', display.bgcolor);
                        $('body').css('background-color', display.bgcolor);
                        scope.$on('$destroy', function () {
                            $('body').css('background-color', '');
                        });
                    });
                });
            }
        }
    }

    /* @ngInject */
    function appRun(routehelper) {
        routehelper.configureRoutes([{
            url: '/displays',
            config: {
                templateUrl: '/_static/app/displays/pages/displays.html',
                controller: 'DisplaysController',
                controllerAs: 'vm',
                title: 'Displays'
            }
        }, {
            url: '/displays/:display',
            config: {
                templateUrl: '/_static/app/displays/pages/display.html',
                controller: 'DisplayController',
                controllerAs: 'vm',
                title: 'Display'
            }
        }, {
            url: '/displays/:group/:display',
            config: {
                templateUrl: '/_static/app/displays/pages/display.html',
                controller: 'DisplayController',
                controllerAs: 'vm',
                title: 'Display'
            }
        }]);
    }
})();
