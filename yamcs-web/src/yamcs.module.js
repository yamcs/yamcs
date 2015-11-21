(function () {
    'use strict';

    angular
        .module('yamcs', [
            'yamcs.core',
            'yamcs.alarms',
            'yamcs.displays',
            'yamcs.home',
            'yamcs.intf',
            'yamcs.mdb'
        ])
        .config(configureModule);

    /* @ngInject */
    function configureModule($locationProvider) {
        $locationProvider.html5Mode({
            enabled: true,
            requireBase: false
        });
    }
})();
