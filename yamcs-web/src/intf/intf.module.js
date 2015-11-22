(function() {
    'use strict';

    angular
        .module('yamcs.intf', [])
        .run(function (alarmsService, eventsService) {
            // Eagerly loading these injected services, to get the subscriptions running
            // Perhaps should research whether we should use angular providers instead.
        });
})();
