(function() {
    'use strict';

    angular
        .module('yamcs.home', ['yamcs.displays'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/', {
            templateUrl: '/_static/yamcs/home/home.html',
            controller: 'HomeController',
            controllerAs: 'vm'
        });
    }
})();
