(function () {
    'use strict';

    angular
        .module('yamcs', [
            'yamcs.core',
            'yamcs.alarms',
            'yamcs.displays',
            'yamcs.events',
            'yamcs.home',
            'yamcs.intf',
            'yamcs.mdb',
            'yamcs.uss'
        ])
        .config(configureModule);

    /* @ngInject */
    function configureModule($locationProvider, $provide) {
        // Remove hash-tags from the URLs
        $locationProvider.html5Mode({
            enabled: true,
            requireBase: false
        });

        // Catch exception messages and store them in root scope to make them available app-wide
        $provide.decorator('$exceptionHandler', /* @ngInject*/ function($delegate, $injector) {
            return function(exception, cause) {
                $delegate(exception, cause);
                var rootScope = $injector.get('$rootScope');
                rootScope.$broadcast('exception', {
                    type: 'danger',
                    msg: exception.message
                });
            };
        })
    }
})();
