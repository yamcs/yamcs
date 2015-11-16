(function() {
    'use strict';

    var core = angular.module('app.core');

    core.value('config', {
        appTitle: 'Yamcs Web',
        version: '1.0.0'
    });

    core.config(configure);

    /* @ngInject */
    function configure ($logProvider, $routeProvider, routehelperConfigProvider) {
        // turn debugging off/on (no info or warn)
        if ($logProvider.debugEnabled) {
            $logProvider.debugEnabled(true);
        }

        // Configure the common route provider
        routehelperConfigProvider.config.$routeProvider = $routeProvider;
        routehelperConfigProvider.config.docTitle = 'Yamcs';
        /*var resolveAlways = { / @ngInject /
            ready: function(dataservice) {
                return dataservice.ready();
            }
            // ready: ['dataservice', function (dataservice) {
            //    return dataservice.ready();
            // }]
        };*/
        //routehelperConfigProvider.config.resolveAlways = resolveAlways;
    }
})();
