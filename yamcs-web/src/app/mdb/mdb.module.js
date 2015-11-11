(function() {
    'use strict';

    angular
        .module('app.mdb', ['app.tm'])
        .run(appRun);

    /* @ngInject */
    function appRun(routehelper) {
        routehelper.configureRoutes([{
            url: '/mdb',
            config: {
                templateUrl: '/_static/app/mdb/pages/index.html',
                controller: 'MDBController',
                controllerAs: 'vm',
                title: 'MDB'
            }
        }, {
            url: '/mdb/parameters',
            config: {
                templateUrl: '/_static/app/mdb/pages/parameters.html',
                controller: 'MDBParametersController',
                controllerAs: 'vm',
                title: 'MDB Parameters'
            }
        }, {
            url: '/mdb/:ss1/parameters',
            config: {
                templateUrl: '/_static/app/mdb/pages/parameters.html',
                controller: 'MDBParametersController',
                controllerAs: 'vm',
                title: 'MDB Parameters'
            }
        }, {
            url: '/mdb/:ss1/:ss2/parameters',
            config: {
                templateUrl: '/_static/app/mdb/pages/parameters.html',
                controller: 'MDBParametersController',
                controllerAs: 'vm',
                title: 'MDB Parameters'
            }
        }, {
            url: '/mdb/containers',
            config: {
                templateUrl: '/_static/app/mdb/pages/containers.html',
                controller: 'MDBContainersController',
                controllerAs: 'vm',
                title: 'MDB Containers'
            }
        }, {
            url: '/mdb/:ss1/containers',
            config: {
                templateUrl: '/_static/app/mdb/pages/containers.html',
                controller: 'MDBContainersController',
                controllerAs: 'vm',
                title: 'MDB Containers'
            }
        }, {
            url: '/mdb/:ss1/:ss2/containers',
            config: {
                templateUrl: '/_static/app/mdb/pages/containers.html',
                controller: 'MDBContainersController',
                controllerAs: 'vm',
                title: 'MDB Containers'
            }
        }, {
            url: '/mdb/commands',
            config: {
                templateUrl: '/_static/app/mdb/pages/commands.html',
                controller: 'MDBCommandsController',
                controllerAs: 'vm',
                title: 'MDB Commands'
            }
        }, {
            url: '/mdb/:ss1/commands',
            config: {
                templateUrl: '/_static/app/mdb/pages/commands.html',
                controller: 'MDBCommandsController',
                controllerAs: 'vm',
                title: 'MDB Commands'
            }
        }, {
            url: '/mdb/:ss1/:ss2/commands',
            config: {
                templateUrl: '/_static/app/mdb/pages/commands.html',
                controller: 'MDBCommandsController',
                controllerAs: 'vm',
                title: 'MDB Commands'
            }
        }, {
            url: '/mdb/:ss1/:name',
            config: {
                templateUrl: '/_static/app/mdb/pages/parameter-detail.html',
                controller: 'ParameterDetailController',
                controllerAs: 'vm',
                title: 'Parameter Detail'
            }
        }, {
            url: '/mdb/:ss1/:ss2/:name',
            config: {
                templateUrl: '/_static/app/mdb/pages/parameter-detail.html',
                controller: 'ParameterDetailController',
                controllerAs: 'vm',
                title: 'Parameter Detail'
            }
        }]);
    }
})();
