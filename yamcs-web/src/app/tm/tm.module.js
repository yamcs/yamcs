(function() {
    'use strict';

    angular
        .module('app.tm', [])
        .run(appRun);

    /* @ngInject */
    function appRun(routehelper) {
        routehelper.configureRoutes([{
            url: '/tm/:ss1/:name',
            config: {
                templateUrl: '/_static/app/tm/pages/parameter-detail.html',
                controller: 'ParameterDetailController',
                controllerAs: 'vm',
                title: 'Parameter Detail'
            }
        }, {
            url: '/tm/:ss1/:ss2/:name',
            config: {
                templateUrl: '/_static/app/tm/pages/parameter-detail.html',
                controller: 'ParameterDetailController',
                controllerAs: 'vm',
                title: 'Parameter Detail'
            }
        }]);
    }
})();
