(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBTemplateController',  MDBTemplateController);

    /* @ngInject */
    function MDBTemplateController(mdbService, yamcsInstance) {
        var vm = this;
        vm.parameters = [];
        vm.yamcsInstance = yamcsInstance;

        mdbService.getSummary().then(function (mdb) {
            vm.mdb = mdb;
            return vm.mdb;
        });
    }
})();
