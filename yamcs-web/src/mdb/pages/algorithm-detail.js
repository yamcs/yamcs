(function() {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBAlgorithmDetailController', MDBAlgorithmDetailController);

    /* @ngInject */
    function MDBAlgorithmDetailController($rootScope, $routeParams, mdbService) {
        var vm = this;
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        var urlname = '/' + $routeParams['ss'] + '/' + encodeURIComponent($routeParams.name);
        vm.urlname = urlname;

        mdbService.getAlgorithmInfo(urlname).then(function (data) {
            vm.info = data;
            return vm.info;
        });
    }
})();
