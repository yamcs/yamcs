(function () {
    'use strict';

    angular
        .module('yamcs.events')
        .controller('EventsController',  EventsController);

    /* @ngInject */
    function EventsController($rootScope, $log, eventsService) {
        var vm = this;
        eventsService.resetUnreadCount(); // Poor man's solution

        $rootScope.pageTitle = 'Events | Yamcs';


        vm.events = [];
        eventsService.listEvents().then(function (data) {
            vm.events = data;
            return vm.events;
        });

        $rootScope.$on('yamcs.eventStats', function(evt, stats) {
            vm.stats = stats;
        });

        vm.reloadData = function() {
        eventsService.resetUnreadCount(); // Not waterproof
        eventsService.listEvents().then(function (data) {
                vm.events = data;
                return vm.events;
            });
        };

        vm.severityToggles = {
            info: true,
            warning: true,
            error: true
        };

        vm.searchText = '';
        vm.filterTable = function(event) {
            var severityMatch = false;
            if (event['severity'] === 'INFO') {
                severityMatch = vm.severityToggles.info;
            } else if (event['severity'] === 'WARNING') {
                severityMatch = vm.severityToggles.warning;
            } else if (event['severity'] === 'ERROR') {
                severityMatch = vm.severityToggles.error;
            } else {
                $log.info('Unexpected event severity ' + event['severity']);
            }

            if (!severityMatch) return false;
            if (!vm.searchText) return true;
            var re = new RegExp(vm.searchText.trim().replace('*', '.*'), 'i');
            return re.test(event['message']) || re.test(event['source']) || re.test(event['type']);
        };
    }
})();
