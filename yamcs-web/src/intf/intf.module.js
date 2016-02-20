(function() {
    'use strict';

    angular
        .module('yamcs.intf', ['yamcs.uss'])// TODO TEMP dep on yamcs.uss used in displays.service.js
        .run(function (alarmsService, eventsService, timeService) {
            // Eagerly loading these injected services, to get the subscriptions running
            // Perhaps should research whether we should use angular providers instead.
        });
})();
