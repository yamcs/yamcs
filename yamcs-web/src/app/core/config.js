(function() {
    'use strict';

    var core = angular.module('app.core');

    core.value('config', {
        appTitle: 'Yamcs Web',
        version: '1.0.0'
    });

    core.config(configure);

    /* @ngInject */
    function configure ($logProvider) {
        // turn debugging off/on (no info or warn)
        if ($logProvider.debugEnabled) {
            $logProvider.debugEnabled(true);
        }
    }
})();
