(function() {
    'use strict';

    angular
        .module('app.alarms', [])
        .run(appRun);

    /* @ngInject */
    function appRun(routehelper) {
        routehelper.configureRoutes([{
            url: '/alarms',
            config: {
                templateUrl: '/_static/app/alarms/pages/alarms.html',
                controller: 'AlarmController',
                controllerAs: 'vm',
                title: 'Alarms'
            }
        }]);
    }
})();
