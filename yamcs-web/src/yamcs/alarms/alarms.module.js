(function() {
    'use strict';

    angular
        .module('yamcs.alarms', [])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/alarms', {
            templateUrl: '/_static/yamcs/alarms/pages/alarms.html',
            controller: 'AlarmController',
            controllerAs: 'vm'
        });
    }
})();
