(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBContainersController',  MDBContainersController);

    /* @ngInject */
    function MDBContainersController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss1'];
        if ($routeParams.hasOwnProperty('ss2')) {
            qname += '/' + $routeParams['ss2'];
        }
        if ($routeParams.hasOwnProperty('ss3')) {
            qname += '/' + $routeParams['ss3'];
        }

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'containers';

        $rootScope.pageTitle = 'Containers | Yamcs';

        mdbService.listContainers(qname).then(function (data) {
            vm.containers = data;
            return vm.containers;
        });
    }
})();
