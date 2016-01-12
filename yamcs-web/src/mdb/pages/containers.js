(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBContainersController',  MDBContainersController);

    /* @ngInject */
    function MDBContainersController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss'];

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'containers';

        $rootScope.pageTitle = 'Containers | Yamcs';

        mdbService.listContainers({
            namespace: qname,
            recurse: (qname === '/yamcs')
        }).then(function (data) {
            vm.containers = data;
            return vm.containers;
        });
    }
})();
