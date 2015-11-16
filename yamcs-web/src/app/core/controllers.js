(function() {
    'use strict';

    angular
        .module('app.core')
        .controller('HeaderController', HeaderController);

    /* @ngInject */
    function HeaderController($location) {
        var vm = this;
        vm.isActive = function (viewLocation) {
            return $location.path().indexOf(viewLocation) == 0;
        };
    }
})();
