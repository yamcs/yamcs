(function() {
    'use strict';

    angular
        .module('yamcs.displays', ['yamcs.core'])
        .directive('displaysPane', displaysPane)
        .directive('embeddedDisplay', embeddedDisplay)
        .config(configure);

    /* @ngInject */
    function displaysPane() {
        return {
            restrict: 'E',
            transclude: true,
            scope: {
                activePane: '@',
                headerTitle: '@',
                yamcsInstance: '=',
                standalone: '=',
                shell: '='
            },
            templateUrl: '/_static/_site/displays/displays.template.html'
        };
    }

    /* @ngInject */
    function embeddedDisplay(displaysService, tmService, $rootScope) {
        return {
            restrict: 'A',
            scope: { ref: '@' },
            link: function(scope, elem, attrs) {
                if (!elem.data('spinner')) {
                    elem.data('spinner', new Spinner());
                }
                elem.data('spinner').spin(elem[0]);

                displaysService.getDisplay(attrs['ref']).then(function(rawDisplay) {
                    USS.drawAndConnectDisplay(rawDisplay, elem, tmService, function(display) {

                        // 'Leak' canvas color
                        // This should not be done here. But I'm not yet fully understanding angular
                        // intricacities when it comes to scopes, directives and DOM manipulations.
                        //elem.parents('.main').css('background-color', display.bgcolor);
                        $('body').css('background-color', display.bgcolor);
                        elem.data('spinner').stop();
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
        $routeProvider.when('/:instance/displays', {
            templateUrl: '/_static/_site/displays/pages/displays.html',
            controller: 'DisplaysController',
            controllerAs: 'vm'
        }).when('/:instance/displays/:display*', {
            templateUrl: '/_static/_site/displays/pages/display.html',
            controller: 'DisplayController',
            controllerAs: 'vm'
        });
    }
})();
