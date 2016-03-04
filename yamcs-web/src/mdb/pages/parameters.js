(function () {
    'use strict';

    angular.module('yamcs.mdb').controller('MDBParametersController',  MDBParametersController);

    /* @ngInject */
    function MDBParametersController($rootScope, mdbService, $routeParams, $filter, yamcsInstance) {
        var vm = this;

        var qname  = '/' + $routeParams['ss'];
        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'parameters';
        vm.includesNested = (qname === '/yamcs');
        vm.parametersLoaded = false;
        vm.hasParameters = undefined;

        $rootScope.pageTitle = 'Parameters | Yamcs';

        mdbService.listParameters({
            namespace: qname,
            recurse: vm.includesNested
        }).then(function (data) {
            var html = '<table class="table"><tr>';
            if (vm.includesNested) {
                html += '<th width="300">Qualified Name</th>';
            } else {
                html += '<th width="300">Name</th>';
            }

            // Yes, this is ugly and should be in the template
            // BUT, Angular 1.x is a bit slow when it comes to large tables.
            // So below I just manually assemble the HTML and use a one-way bind
            // to the template. The Filters as well use about 1/4th of the needed time,
            // maybe sth should be done there as well.
            //
            // The MID-TERM solution is to wait for Angular 2, which will have
            // similar fast DOM update support as is now available in frameworks like React.

            html += '<th width="100">Type</th>' +
                '<th width="100">Units</th>' +
                '<th width="100">Data Source</th>' +
                '<th>Description</th>' +
                '</tr>';
            var sorted = $filter('orderBy')(data, 'qualifiedName');
            for (var i = 0; i < sorted.length; i++) {
                html += '<tr>';

                html += '<td><a href="/' + yamcsInstance + '/mdb' + sorted[i]['qualifiedName'] + '">';
                if (vm.includesNested) {
                    html += sorted[i]['qualifiedName'];
                } else {
                    html += sorted[i]['name'];
                }
                html += '</a></td>';

                if (sorted[i]['type'] && sorted[i]['type']['engType']) {
                    html += '<td>' + $filter('capitalize')(sorted[i]['type']['engType']) + '</td>';
                } else {
                    html += '<td>-</td>';
                }

                if (sorted[i]['type'] && sorted[i]['type']['unitSet']) {
                    html += '<td>' + $filter('joinBy')(sorted[i]['type']['unitSet'], 'unit') + '</td>';
                } else {
                    html += '<td>-</td>';
                }

                if (sorted[i]['dataSource']) {
                    html += '<td>' + $filter('capitalize')(sorted[i]['dataSource']) + '</td>';
                } else {
                    html += '<td>-</td>';
                }

                if (sorted[i]['shortDescription']) {
                    html += '<td>' + sorted[i]['shortDescription'] + '</td>';
                } else {
                    html += '<td>-</td>';
                }
                html += '</tr>';
            }
            html += '</table>';

            vm.rawParameterTable = html;

            //vm.parameters = data; // TODO Wait for Angular 2
            vm.parametersLoaded = true;
            vm.hasParameters = (data.length > 0);

            return vm.parameters;
        });
    }
})();
