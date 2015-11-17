(function() {
    'use strict';

    angular
        .module('app.displays', ['app.tm'])
        .directive('displaysPane', displaysPane)
        .directive('embeddedDisplay', embeddedDisplay)
        .config(configure);

    /* @ngInject */
    function displaysPane() {
        return {
            restrict: 'E',
            transclude: true,
            scope: { activePane: '@', headerTitle: '@' },
            templateUrl: '/_static/app/displays/displays.template.html'
        };
    }

    /* @ngInject */
    function embeddedDisplay(displaysService, tmService, $rootScope) {
        return {
            restrict: 'A',
            scope: { ref: '@' },
            link: function(scope, elem, attrs) {
                var spinner = new Spinner();
                spinner.spin(elem[0]);
                displaysService.getDisplay(attrs['ref']).then(function(rawDisplay) {
                    USS.drawAndConnectDisplay(rawDisplay, elem, tmService, function(display) {

                        // 'Leak' canvas color
                        // This should not be done here. But I'm not yet fully understanding angular
                        // intricacities when it comes to scopes, directives and DOM manipulations.
                        //elem.parents('.main').css('background-color', display.bgcolor);
                        $('body').css('background-color', display.bgcolor);
                        spinner.stop();
                        scope.$on('$destroy', function () {
                            $('body').css('background-color', '');
                        });
                    });
                });
            }
        }
    }

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/displays', {
            templateUrl: '/_static/app/displays/pages/displays.html',
            controller: 'DisplaysController',
            controllerAs: 'vm'
        }).when('/displays/:display', {
            templateUrl: '/_static/app/displays/pages/display.html',
            controller: 'DisplayController',
            controllerAs: 'vm'
        }).when('/displays/:group/:display', {
            templateUrl: '/_static/app/displays/pages/display.html',
            controller: 'DisplayController',
            controllerAs: 'vm'
        });
    }
})();
