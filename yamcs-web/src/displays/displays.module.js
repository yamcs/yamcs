(function() {
    'use strict';

    angular
        .module('yamcs.displays', ['yamcs.core', 'yamcs.uss'])
        .directive('displaysPane', displaysPane)
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
