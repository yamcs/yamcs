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

        $rootScope.pageTitle = 'Parameters | Yamcs';

        console.log('listing for ' + qname);
        mdbService.listParameters(qname).then(function (data) {
            vm.parameters = data;
            return vm.parameters;
        });
    }
})();
