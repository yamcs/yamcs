(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBTemplateController',  MDBTemplateController);

    /* @ngInject */
    function MDBTemplateController(mdbService) {
        var vm = this;
        vm.parameters = [];

        mdbService.getSummary().then(function (mdb) {
            vm.mdb = mdb;
            return vm.mdb;
        });
    }
})();
