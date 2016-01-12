(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBParametersController',  MDBParametersController);

    /* @ngInject */
    function MDBParametersController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname  = '/' + $routeParams['ss'];
        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'parameters';
        vm.includesNested = includesNested;

        $rootScope.pageTitle = 'Parameters | Yamcs';

        mdbService.listParameters({
            namespace: qname,
            recurse: includesNested()
        }).then(function (data) {
            vm.parameters = data;
            return vm.parameters;
        });

        function includesNested() {
            return qname === '/yamcs';
        }
    }
})();
