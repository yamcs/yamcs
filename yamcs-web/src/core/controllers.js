(function() {
    'use strict';

    angular
        .module('yamcs.core')
        .controller('HeaderController', HeaderController);

    /* @ngInject */
    function HeaderController($location, yamcsInstance) {
        var vm = this;
        vm.isActive = function (viewLocation) {
            return $location.path().indexOf('/' + yamcsInstance + viewLocation) === 0;
        };
    }
})();
