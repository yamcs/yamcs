(function () {
    'use strict';

    angular.module('yamcs.mdb').controller('MDBContainersController',  MDBContainersController);

    /* @ngInject */
    function MDBContainersController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss'];

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'containers';
        vm.containersLoaded = false;

        $rootScope.pageTitle = 'Containers | Yamcs';

        mdbService.listContainers({
            namespace: qname,
            recurse: (qname === '/yamcs')
        }).then(function (data) {
            vm.containers = data;
            vm.containersLoaded = true;
            return vm.containers;
        });
    }
})();
