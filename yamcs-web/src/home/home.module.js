(function() {
    'use strict';

    angular
        .module('yamcs.home', ['yamcs.displays'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/:instance', {
            templateUrl: '/_static/_site/home/home.html',
            controller: 'HomeController',
            controllerAs: 'vm'
        });
    }
})();
