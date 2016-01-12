(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBAlgorithmsController',  MDBAlgorithmsController);

    /* @ngInject */
    function MDBAlgorithmsController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss'];

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'algorithms';

        $rootScope.pageTitle = 'Algorithms | Yamcs';

        mdbService.listAlgorithms({
            namespace: qname,
            recurse: (qname === '/yamcs')
        }).then(function (data) {
            vm.algorithms = data;
            return vm.algorithms;
        });
    }
})();
