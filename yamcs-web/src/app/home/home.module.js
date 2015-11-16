(function() {
    'use strict';

    angular
        .module('app.home', ['app.displays'])
        .run(appRun);

    /* @ngInject */
    function appRun(routehelper) {
        routehelper.configureRoutes([{
            url: '/',
            config: {
                templateUrl: '/_static/app/home/home.html',
                title: 'Home'
            }
        }]);
    }
})();
