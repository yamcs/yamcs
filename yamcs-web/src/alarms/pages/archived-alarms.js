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
    }
})();
