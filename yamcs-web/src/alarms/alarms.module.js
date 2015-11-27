(function() {
    'use strict';

    angular
        .module('yamcs.alarms', [])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/:instance/alarms', {
            templateUrl: '/_static/_site/alarms/pages/alarms.html',
            controller: 'AlarmsController',
            controllerAs: 'vm'
        });
        $routeProvider.when('/:instance/alarms/archive', {
            templateUrl: '/_static/_site/alarms/pages/archived-alarms.html',
            controller: 'ArchivedAlarmsController',
            controllerAs: 'vm'
        });
    }
})();
