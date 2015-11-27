(function () {
    'use strict';

    angular
        .module('yamcs.alarms')
        .controller('ArchivedAlarmsController',  ArchivedAlarmsController);

    /* @ngInject */
    function ArchivedAlarmsController($rootScope, alarmsService) {
        $rootScope.pageTitle = 'Archived Alarms | Yamcs';

        var vm = this;
        vm.alarmTab = 'archivedAlarms';

        // Alarms sorted by key. Combines state of realtime and history
        vm.alarms = [];
        alarmsService.listAlarms().then(function (alarms) {
            vm.alarms = alarms;
        });

        vm.searchText = '';
        vm.filterTable = function(value) {
            if (!vm.searchText) return true;
            var qname = value['triggerValue']['id']['name'];
            var regex = new RegExp(vm.searchText.trim().replace('*', '.*'), 'i');
            return regex.test(qname);
        };

        vm.expandAlarms = function() {
            for (var i = 0; i < vm.alarms.length; i++) {
                vm.alarms[i].expanded = true;
            }
        };

        vm.collapseAlarms = function() {
            for (var i = 0; i < vm.alarms.length; i++) {
                vm.alarms[i].expanded = false;
            }
        };
    }
})();
