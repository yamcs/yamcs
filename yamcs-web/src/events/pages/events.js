(function () {
    'use strict';

    angular
        .module('yamcs.events')
        .controller('EventsController',  EventsController);

    /* @ngInject */
    function EventsController($rootScope, $scope, $log, eventsService) {
        var vm = this;
        eventsService.resetUnreadCount(); // Poor man's solution

        $rootScope.pageTitle = 'Events | Yamcs';

        $scope.ctx = {
            loadingMoreData: false
        };

        $scope.loadMoreEvents = function() {
            if (!vm.events.length || $scope.ctx.loadingMoreData) return;
            $scope.ctx.loadingMoreData = true;

            var finalEvent = vm.events[vm.events.length - 1];
            console.log('loading more data from ' + finalEvent['generationTimeUTC']);

            eventsService.listEvents({
                limit: 20,
                stop: finalEvent['generationTimeUTC']
            }).then(function (data) { // todo check when end is reached, to prevent further triggers
                vm.events.push.apply(vm.events, data);
                $scope.ctx.loadingMoreData = false;
                return vm.events;
            });
        };


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
            eventsService.listEvents({
                limit: 200
            }).then(function (data) {
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
