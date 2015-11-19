(function () {
    'use strict';

    angular.module('yamcs.mdb')
        .directive('mdbPane', function () {
            return {
                restrict: 'E',
                transclude: true,
                scope: { activePane:'@' },
                templateUrl: '/_static/yamcs/mdb/mdb.template.html'
            };
    });
})();
