(function () {
    'use strict';

    angular.module('yamcs.mdb')
        .directive('mdbPane', function () {
            return {
                restrict: 'E',
                transclude: true,
                scope: {
                    activePane: '@',
                    yamcsInstance: '=',
                    shell: '='
                },
                templateUrl: '/_static/_site/mdb/mdb.template.html'
            };
    });
})();
