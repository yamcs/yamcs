(function () {
    'use strict';

    angular
        .module('yamcs.events')
        .controller('EventsController',  EventsController);

    /* @ngInject */
    function EventsController($rootScope, eventsService) {
        var vm = this;
        eventsService.resetUnreadCount(); // Poor man's solution

        $rootScope.pageTitle = 'Events | Yamcs';

        vm.events = eventsService.getEvents();

        /*alarmService.listAlarms().then(function (data) {
            vm.alarms = data;
            return vm.alarms;
        });*/

        $rootScope.$on('yamcs.eventStats', function(evt, stats) {
            vm.events = eventsService.getEvents();
        });
    }
})();
