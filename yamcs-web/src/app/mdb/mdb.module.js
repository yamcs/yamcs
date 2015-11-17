(function() {
    'use strict';

    angular
        .module('app.mdb', ['app.tm'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/mdb', {
            templateUrl: '/_static/app/mdb/pages/index.html',
            controller: 'MDBIndexController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/parameters', {
            templateUrl: '/_static/app/mdb/pages/parameters.html',
            controller: 'MDBParametersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/parameters', {
            templateUrl: '/_static/app/mdb/pages/parameters.html',
            controller: 'MDBParametersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/containers', {
            templateUrl: '/_static/app/mdb/pages/containers.html',
            controller: 'MDBContainersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/containers', {
            templateUrl: '/_static/app/mdb/pages/containers.html',
            controller: 'MDBContainersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/commands', {
            templateUrl: '/_static/app/mdb/pages/commands.html',
            controller: 'MDBCommandsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/commands', {
            templateUrl: '/_static/app/mdb/pages/commands.html',
            controller: 'MDBCommandsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/algorithms', {
            templateUrl: '/_static/app/mdb/pages/algorithms.html',
            controller: 'MDBAlgorithmsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/algorithms', {
            templateUrl: '/_static/app/mdb/pages/algorithms.html',
            controller: 'MDBAlgorithmsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/algorithms/:name', {
            templateUrl: '/_static/app/mdb/pages/algorithm-detail.html',
            controller: 'MDBAlgorithmDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/algorithms/:name', {
            templateUrl: '/_static/app/mdb/pages/algorithm-detail.html',
            controller: 'MDBAlgorithmDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:name', {
            templateUrl: '/_static/app/mdb/pages/parameter-detail.html',
            controller: 'MDBParameterDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/:name', {
            templateUrl: '/_static/app/mdb/pages/parameter-detail.html',
            controller: 'MDBParameterDetailController',
            controllerAs: 'vm'
        });
    }
})();
