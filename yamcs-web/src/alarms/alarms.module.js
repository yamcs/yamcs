(function() {
    'use strict';

    angular
        .module('yamcs.alarms', [])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/alarms', {
            templateUrl: '/_static/_site/alarms/pages/alarms.html',
            controller: 'AlarmController',
            controllerAs: 'vm'
        });
    }
})();
