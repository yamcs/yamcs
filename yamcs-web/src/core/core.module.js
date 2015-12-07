(function() {
    'use strict';

    angular
        .module('yamcs.core', [
            'ngAnimate', 'ngRoute', 'ngSanitize',
            'ui.bootstrap', 'infinite-scroll',
            'yamcs.intf'
        ])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.otherwise({
            templateUrl: '/_static/_site/core/404.html'
        });
    }
})();
