(function() {
    'use strict';

    angular
        .module('yamcs.mdb', ['yamcs.tm'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/mdb', {
            templateUrl: '/_static/_site/mdb/pages/index.html',
            controller: 'MDBIndexController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/parameters', {
            templateUrl: '/_static/_site/mdb/pages/parameters.html',
            controller: 'MDBParametersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/parameters', {
            templateUrl: '/_static/_site/mdb/pages/parameters.html',
            controller: 'MDBParametersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/containers', {
            templateUrl: '/_static/_site/mdb/pages/containers.html',
            controller: 'MDBContainersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/containers', {
            templateUrl: '/_static/_site/mdb/pages/containers.html',
            controller: 'MDBContainersController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/commands', {
            templateUrl: '/_static/_site/mdb/pages/commands.html',
            controller: 'MDBCommandsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/commands', {
            templateUrl: '/_static/_site/mdb/pages/commands.html',
            controller: 'MDBCommandsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/algorithms', {
            templateUrl: '/_static/_site/mdb/pages/algorithms.html',
            controller: 'MDBAlgorithmsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/algorithms', {
            templateUrl: '/_static/_site/mdb/pages/algorithms.html',
            controller: 'MDBAlgorithmsController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/algorithms/:name', {
            templateUrl: '/_static/_site/mdb/pages/algorithm-detail.html',
            controller: 'MDBAlgorithmDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/algorithms/:name', {
            templateUrl: '/_static/_site/mdb/pages/algorithm-detail.html',
            controller: 'MDBAlgorithmDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:name', {
            templateUrl: '/_static/_site/mdb/pages/parameter-detail.html',
            controller: 'MDBParameterDetailController',
            controllerAs: 'vm'
        }).when('/mdb/:ss1/:ss2/:name', {
            templateUrl: '/_static/_site/mdb/pages/parameter-detail.html',
            controller: 'MDBParameterDetailController',
            controllerAs: 'vm'
        });
    }
})();
