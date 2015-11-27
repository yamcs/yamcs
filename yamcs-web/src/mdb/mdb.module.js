(function() {
    'use strict';

    angular
        .module('yamcs.mdb', ['yamcs.core'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/:instance/mdb', {
            templateUrl: '/_static/_site/mdb/pages/index.html',
            controller: 'MDBIndexController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/parameters', {
            templateUrl: '/_static/_site/mdb/pages/parameters.html',
            controller: 'MDBParametersController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/containers', {
            templateUrl: '/_static/_site/mdb/pages/containers.html',
            controller: 'MDBContainersController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/commands', {
            templateUrl: '/_static/_site/mdb/pages/commands.html',
            controller: 'MDBCommandsController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/algorithms', {
            templateUrl: '/_static/_site/mdb/pages/algorithms.html',
            controller: 'MDBAlgorithmsController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/algorithms/:name', {
            templateUrl: '/_static/_site/mdb/pages/algorithm-detail.html',
            controller: 'MDBAlgorithmDetailController',
            controllerAs: 'vm'
        }).when('/:instance/mdb/:ss*/:name', {
            templateUrl: '/_static/_site/mdb/pages/parameter-detail.html',
            controller: 'MDBParameterDetailController',
            controllerAs: 'vm'
        });
    }
})();
