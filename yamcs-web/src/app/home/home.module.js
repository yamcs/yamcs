(function() {
    'use strict';

    angular
        .module('app.home', ['app.displays'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/', {
            templateUrl: '/_static/app/home/home.html'
        });
    }
})();
