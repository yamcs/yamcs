(function() {
    'use strict';

    angular
        .module('app.tm')
        .directive('plot', plot);

    /* @ngInject */
    function plot($log, $filter, $http, exception) {

        return {
            restrict: 'A',
            scope: { pinfo: '=', para: '=', 'plotmode': '=' },
            link: function(scope, element, attrs) {

                var data = [];
                var valueRange = [null, null];

                var plotEl = angular.element('<div style="width: 100%"></div>');
                element.prepend(plotEl);
                makePlot(plotEl[0], scope, data, valueRange);
            }
        };

        function makePlot(containingDiv, scope, data, valueRange) {
            valueRange = calculateInitialPlotRange(scope.pinfo);
            var guidelines = calculateGuidelines(scope.pinfo);
            var ctx = {archiveFetched: false};

            var g = new Dygraph(containingDiv, 'X\n', {
                drawPoints: true,
                showRoller: false,
                customBars: true,
                gridLineColor: 'lightgray',
                axisLabelColor: '#666',
                axisLabelFontSize: 11,
                digitsAfterDecimal: 6,
                labels: ['Time', 'Value'],
                valueRange: valueRange,
                underlayCallback: function(canvasCtx, area, g) {
                    // First draw rects
                    var prevAlpha = canvasCtx.globalAlpha;
                    canvasCtx.globalAlpha = 0.2;
                    guidelines.forEach(function(guideline) {
                        if (guideline['y2'] === null)
                            return;

                        var y1 = g.toDomCoords(0, guideline['y1'])[1];
                        canvasCtx.fillStyle = guideline['color'];
                        var y2 = g.toDomCoords(0, guideline['y2'])[1];
                        canvasCtx.fillRect(
                            area.x, Math.min(y1, y2),
                            area.w, Math.abs(y2 - y1)
                        );
                    });
                    canvasCtx.globalAlpha = prevAlpha;

                    // Then lines on top (todo: verify this works)
                    guidelines.forEach(function(guideline) {
                        if (guidelines['y2'] !== null)
                            return;

                        var y1 = g.toDomCoords(0, guideline['y1'])[1];
                        canvasCtx.fillStyle = guideline['color'];
                        canvasCtx.fillRect(area.x, y1, area.w, 1);
                    });
                }
            });

            var tempData = [];
            scope.$watch('para', function(pval) {
                addPval(g, pval, data, valueRange, ctx, tempData);
            });

            scope.$watch('plotmode', function(plotmode) {
                var qname = scope.pinfo.qualifiedName;

                // Reset first
                g.resetZoom();
                valueRange = calculateInitialPlotRange(scope.pinfo);
                data.length = 0;

                // Add new set of data
                loadHistoricData(g, qname, plotmode, data, valueRange, ctx);
            });

            return g;
        }

        /**
         * Returns an array of y-coordinates for indicating OOL ranges
         */
        function calculateGuidelines(pinfo) {
            var guidelines = [];
            if (pinfo.hasOwnProperty('type') && pinfo['type'].hasOwnProperty('defaultAlarm')) {
                var defaultAlarm = pinfo['type']['defaultAlarm'];
                if (defaultAlarm.hasOwnProperty('staticAlarmRange')) {

                    // LOW LIMITS
                    var last_y = null;
                    var i, range, guideline;
                    for (i = defaultAlarm['staticAlarmRange'].length - 1; i >= 0; i--) {
                        range = defaultAlarm['staticAlarmRange'][i];
                        if (range.hasOwnProperty('minInclusive')) {
                            guideline = {
                                y1: range['minInclusive'],
                                y2: last_y,
                                color: colorForLevel(range['level'])
                            };
                            guidelines.push(guideline);
                            last_y = guideline['y1'];
                        }
                    }

                    // HIGH LIMITS
                    last_y = null;
                    for (i = defaultAlarm['staticAlarmRange'].length - 1; i >= 0; i--) {
                        range = defaultAlarm['staticAlarmRange'][i];
                        if (range.hasOwnProperty('maxInclusive')) {
                            guideline = {
                                y1: range['maxInclusive'],
                                y2: last_y,
                                color: colorForLevel(range['level'])
                            };
                            guidelines.push(guideline);
                            last_y = guideline['y1'];
                        }
                    }
                }
            }
            return guidelines;
        }

        /**
         * Provides an initial y-range based on static alarm ranges. The intent
         * is to make sure the horizontal guidelines always get shown regardless
         * of actual data.
         *
         * Needs to be adjusted when data gets updated in case it exceeds any
         * range.
         */
        function calculateInitialPlotRange(pinfo) {
            var min = null;
            var max = null;
            if (pinfo.hasOwnProperty('type') && pinfo['type'].hasOwnProperty('defaultAlarm')) {
                var defaultAlarm = pinfo['type']['defaultAlarm'];
                if (defaultAlarm.hasOwnProperty('staticAlarmRange')) {
                    defaultAlarm['staticAlarmRange'].forEach(function (range) {
                        if (range.hasOwnProperty('minInclusive')) {
                            var newMin = range['minInclusive'];
                            min = (min === null) ? newMin : Math.min(newMin, min);
                        }
                        if (range.hasOwnProperty('maxInclusive')) {
                            var newMax = range['maxInclusive'];
                            max = (max === null) ? newMax : Math.max(newMax, max);
                        }
                    });
                }
            }

            // Hmm null appears to be interpreted as 0...
            if (min === null) min = max;
            if (max === null) max = min;
            return [min, max];
        }

        function addPval(g, pval, data, valueRange, ctx, tempData) {
            if (pval && pval.hasOwnProperty('engValue')) {
                var val = $filter('stringValue')(pval);
                updateValueRange(g, valueRange, val, val);

                var t = new Date();
                t.setTime(Date.parse(pval['generationTimeUTC']));

                if (ctx['archiveFetched']) {
                    data.push([t, [val,val,val]]);
                    updateGraph(g, data);
                } else {
                    tempData.push([t, [val,val,val]]);
                }
            }
        }

        /**
         * Ensures the valueRange contains at least the provided
         * low and high values
         */
        function updateValueRange(g, valueRange, low, high) {
            if ((typeof valueRange[0] !== 'undefined') && (typeof high !== 'undefined')) {
                valueRange[0] = Math.min(valueRange[0], low);
                g.updateOptions({ valueRange: valueRange });
            }
            if ((typeof valueRange[1] !== 'undefined') && (typeof high !== 'undefined')) {
                valueRange[1] = Math.max(valueRange[1], high);
                g.updateOptions({ valueRange: valueRange });
            }
        }

        function updateGraph(g, data) {
            var options = {
                file: data,
                drawPoints: data.length < 50
            };
            g.updateOptions(options);
        }


        function loadHistoricData(g, qname, plotMode, data, valueRange, ctx) {
            var now = new Date();
            var nowIso = now.toISOString();
            var before = new Date(now.getTime());
            var beforeIso = nowIso;
            if (plotMode === '15m') {
                before.setMinutes(now.getMinutes() - 15);
                beforeIso = before.toISOString();
            } else if (plotMode === '30m') {
                before.setMinutes(now.getMinutes() - 30);
                beforeIso = before.toISOString();
            } else if (plotMode === '1h') {
                before.setHours(now.getHours() - 1);
                beforeIso = before.toISOString();
            } else if (plotMode === '5h') {
                before.setHours(now.getHours() - 5);
                beforeIso = before.toISOString();
            } else if (plotMode === '1d') {
                before.setDate(now.getDate() - 1);
                beforeIso = before.toISOString();
            } else if (plotMode === '1w') {
                before.setDate(now.getDate() - 7);
                beforeIso = before.toISOString();
            } else if (plotMode === '1m') {
                before.setDate(now.getDate() - 31);
                beforeIso = before.toISOString();
            } else if (plotMode === '3m') {
                before.setDate(now.getDate() - (3*31));
                beforeIso = before.toISOString();
            }

            ctx['archiveFetched'] = false;
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/archive/' + yamcsInstance + '/parameters' + qname + '/samples';
            targetUrl += '?start=' + beforeIso.slice(0, -1);
            targetUrl += '&stop=' + nowIso.slice(0, -1);
            $http.get(targetUrl).then(function (response) {
                var min, max;
                if (response.data['sample']) {
                    for (var i = 0; i < response.data['sample'].length; i++) {
                        var sample = response.data['sample'][i];
                        var t = new Date();
                        t.setTime(Date.parse(sample['averageGenerationTimeUTC']));
                        var v = sample['averageValue'];
                        var lo = sample['lowValue'];
                        var hi = sample['highValue'];

                        if (typeof min === 'undefined') {
                            min = lo;
                            max = hi;
                        } else {
                            if (min > lo) min = lo;
                            if (max < hi) max = hi;
                        }
                        data.push([t, [lo, v, hi]]);
                    }
                }
                updateValueRange(g, valueRange, min, max);


                if (data.length > 0) {
                    updateGraph(g, data);
                } else {
                    updateGraph(g, 'x\n');
                }

                ctx['archiveFetched'] = true;
            }).catch (function (message) {
                exception.catcher('XHR failed')(message);
            });
        }

        function colorForLevel(level) {
            if (level == 'WATCH') return '#ffff99';
            else if (level == 'WARNING') return '#ffff04';
            else if (level == 'DISTRESS') return '#ff6601';
            else if (level == 'CRITICAL') return '#ff0201';
            else if (level == 'SEVERE') return '#800000';
            else $log.error('Unknown level ' + level);
        }
    }
})();
