(function() {
    'use strict';

    angular
        .module('app.layout')
        .controller('Shell', Shell);

    /* @ngInject */
    function Shell($scope, $location, socket, alarmService, configService) {
        var vm = this;

        vm.socketOpen = false;
        vm.socketInfoType = 'danger';

        vm.isActive = function (viewLocation) {
            return $location.path().indexOf(viewLocation) === 0;
        };

        vm.appTitle = configService.get('title');
        vm.brandImage = configService.get('brandImage');

        socket.on('open', function () {
            vm.socketOpen = true;
            vm.socketInfoType = 'success';
        });
        socket.on('close', function () {
            vm.socketOpen = false;
            vm.socketInfoType = 'danger';
        });

        /*
            ALARM STATS
         */
        vm.alarmCount = 0;
        vm.alarmBadgeColor = '#9d9d9d';

        var alarmSubscriptionId = alarmService.watch(handleAggregatedAlarmState);

        /*
            Terminate subscription when the controller is destroyed
         */
        $scope.$on('$destroy', function() {
            console.log('alarm/unsubscribe of shell controller');
            alarmService.unwatch(alarmSubscriptionId);
        });

        function handleAggregatedAlarmState(data) {
            console.log('asked to update alarm state ', data);
            vm.alarmCount = data.length;

            var needsAck = false;
            for (var i = 0; i < data.length; i++) {
                if (!data[i]['acknowledgeInfo']) {
                    needsAck = true;
                    break;
                }
            }
            vm.alarmBadgeColor = needsAck ? '#c9302c' : '#9d9d9d';
        }
    }
})();
