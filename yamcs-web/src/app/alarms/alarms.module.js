(function() {
    'use strict';

    angular
        .module('app.alarms', [])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/alarms', {
            templateUrl: '/_static/app/alarms/pages/alarms.html',
            controller: 'AlarmController',
            controllerAs: 'vm'
        });
    }
})();
