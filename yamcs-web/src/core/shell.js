(function() {
    'use strict';

    angular
        .module('yamcs.core')
        .config(configureModule)
        .controller('Shell', Shell);

    function configureModule() {
        toastr.options = {
            tapToDismiss: true,
            debug: false,
            positionClass: 'toast-position'
        }
    }

    /* @ngInject */
    function Shell($rootScope, $scope, $location, socket, configService) {
        var vm = this;

        vm.socketOpen = false;
        vm.socketInfoType = 'danger';

        vm.isActive = function (viewLocation) {
            return $location.path().indexOf(viewLocation) === 0;
        };

        vm.appTitle = configService.get('title');
        vm.brandImage = configService.get('brandImage');

        /*
            MESSAGE CENTER
            (currently used only for page-scoped errors)
         */
        vm.messages = [];
        vm.closeMessage = function (idx) {
            vm.messages.splice(idx, 1);
        };
        $rootScope.$on('exception', function(evt, message) {
            vm.messages.push(message);
        });
        $rootScope.$on('$routeChangeStart', function(next, current) {
            vm.messages = [];
        });

        /*
            LIVE CONNECTION
         */
        socket.on('open', function () {
            vm.socketOpen = true;
            vm.socketInfoType = 'success';
        });
        socket.on('close', function () {
            vm.socketOpen = false;
            vm.socketInfoType = 'danger';
        });

        /*
            EVENT STATS
         */
        vm.alarmBadgeColor = '#9d9d9d';
        $rootScope.$on('yamcs.eventStats', function (evt, stats) {
            vm.eventStats = stats;
        });
        if (configService.get('enableNotifications', true)) {
            $rootScope.$on('yamcs.event', function (evt, event) {
                if (event['severity'] === 'ERROR') {
                    toastr.error(event['message']);
                } else if (event['severity'] === 'WARNING') {
                    toastr.warning(event['message']);
                } else {
                    toastr.info(event['message']);
                }
            });
        }

        /*
            ALARM STATS
         */
        vm.alarmCount = 0;
        vm.alarmBadgeColor = '#9d9d9d';

        $rootScope.$on('yamcs.alarmStats', function (evt, stats) {
            vm.alarmStats = stats;
        });

        $rootScope.$on('yamcs.time', function (evt, data) {
            vm.timeData = data;
        });
    }
})();
