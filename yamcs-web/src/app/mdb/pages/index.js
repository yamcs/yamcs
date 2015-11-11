(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBController',  MDBController);

    /* @ngInject */
    function MDBController(mdbService, $location) {
        var vm = this;
        vm.parameters = [];
        vm.title = 'Mission Database';

        // Flatten the nested structure, for better UI
        mdbService.getSummary().then(function (data) {
            data['flatSpaceSystems'] = [];
            if (data.hasOwnProperty('spaceSystem')) {
                for (var i = 0; i < data.spaceSystem.length; i++) {
                    var flattened = flattenSpaceSystem(data.spaceSystem[i]);
                    for (var j = 0; j < flattened.length; j++) {
                        data['flatSpaceSystems'].push(flattened[j]);
                    }
                }
            }
            vm.mdb = data;
            return vm.mdb;
        });
    }

    function flattenSpaceSystem(ss) {
        var flattened = [ ss ];
        if (ss.hasOwnProperty('sub')) {
            for (var i = 0; i < ss.sub.length; i++) {
                var flatsub = flattenSpaceSystem(ss.sub[i]);
                for (var j = 0; j < flatsub.length; j++) {
                    flattened.push(flatsub[j]);
                }
            }
        }
        return flattened;
    }
})();
