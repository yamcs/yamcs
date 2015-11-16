(function() {
    'use strict';

    angular
        .module('app.core')
        .factory('exception', exception);

    /* @ngInject */
    function exception($log) {
        return {
            catcher: catcher
        };

        function catcher(message) {
            return function(reason) {
                $log.error(message, reason);
            };
        }
    }
})();
