(function () {
    'use strict';

    angular.module('app.mdb')
        .directive('mdbPane', function () {
            return {
                restrict: 'E',
                transclude: true,
                scope: { activePane:'@', title: '@' },
                templateUrl: '/_static/app/mdb/mdb.template.html'
            };
    });
})();
