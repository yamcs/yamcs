(function() {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBAlgorithmDetailController', MDBAlgorithmDetailController);

    /* @ngInject */
    function MDBAlgorithmDetailController($routeParams, mdbService) {
        var vm = this;
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        var urlname = '/' + $routeParams.name;
        if ($routeParams.hasOwnProperty('ss2')) {
            urlname = '/' + $routeParams['ss2'] + urlname;
        }
        if ($routeParams.hasOwnProperty('ss1')) {
            urlname = '/' + $routeParams['ss1'] + urlname;
        }
        vm.urlname = urlname;

        mdbService.getAlgorithmInfo(urlname).then(function (data) {
            vm.info = data;
            return vm.info;
        });
    }
})();
