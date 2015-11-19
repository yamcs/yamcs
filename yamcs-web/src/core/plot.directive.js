(function() {
    'use strict';

    angular
        .module('yamcs.core')
        .directive('plot', plot);

    /* @ngInject */
    function plot($log, $filter, tmService, configService) {

        return {
            restrict: 'EA',
            scope: { pinfo: '=', para: '=', 'range': '=' },
            link: function(scope, element, attrs) {
                var data = [];
                var valueRange = [null, null];
                var plotWrapper = angular.element('<div>');

                var spinContainer = angular.element('<div style="position: absolute; top: 50%; left: 50%;"></div>');
                plotWrapper.prepend(spinContainer);

                var plotEl = angular.element('<div style="width: 100%;"></div>');
                plotWrapper.prepend(plotEl);

                element.prepend(plotWrapper);
                makePlot(plotEl[0], spinContainer, scope, data, valueRange);
            }
        };

        function makePlot(containingDiv, spinContainer, scope, data, valueRange) {
            valueRange = calculateInitialPlotRange(scope.pinfo);
            var guidelines = calculateGuidelines(scope.pinfo);
            var ctx = {archiveFetched: false};
            var spinner = new Spinner();

            var g = new Dygraph(containingDiv, 'X\n', {
                drawPoints: true,
                showRoller: false,
                customBars: true,
                animatedZooms: true,
                gridLineColor: 'lightgray',
                axisLabelColor: '#666',
                axisLabelFontSize: 11,
                digitsAfterDecimal: 6,
                labels: ['Time', 'Value'],
                labelsDiv: 'parameter-detail-legend',
                valueRange: valueRange,
                yRangePad: 10,
                axes: {
                    y: { axisLabelWidth: 50 }
                },
                rightGap: 0,
                labelsUTC: configService.get('utcOnly', false),
                underlayCallback: function(canvasCtx, area, g) {
                    var prevAlpha = canvasCtx.globalAlpha;
                    canvasCtx.globalAlpha = 0.2;

                    if (data.length === 0 && ctx.archiveFetched) {
                        canvasCtx.font = '20px Verdana';
                        canvasCtx.textAlign = 'center';
                        canvasCtx.textBaseline = 'middle';
                        var textX = area.x + (area.w / 2);
                        var textY = area.y + (area.h / 2);
                        canvasCtx.fillText('no data for this range', textX, textY);
                    }

                    guidelines.forEach(function(guideline) {
                        if (guideline['y2'] === null)
                            return;

                        var y1, y2;
                        if (guideline['y1'] === -Infinity) {
                            y1 = area.y + area.h;
                        } else {
                            y1 = g.toDomCoords(0, guideline['y1'])[1];
                        }

                        if (guideline['y2'] === Infinity) {
                            y2 = 0;
                        } else {
                            y2 = g.toDomCoords(0, guideline['y2'])[1];
                        }

                        canvasCtx.fillStyle = guideline['color'];
                        canvasCtx.fillRect(
                            area.x, Math.min(y1, y2),
                            area.w, Math.abs(y2 - y1)
                        );
                    });
                    canvasCtx.globalAlpha = prevAlpha;
                }
            });

            g.ready(function () {
                spinner.spin(spinContainer[0]);
            });

            var tempData = [];
            scope.$watch('para', function(pval) {
                addPval(g, pval, data, valueRange, ctx, tempData);
            });

            scope.$watch('range', function(range) {
                var qname = scope.pinfo.qualifiedName;

                if (data.length > 0) {
                    g.resetZoom();
                }

                valueRange = calculateInitialPlotRange(scope.pinfo);
                data.length = 0;

                // Add new set of data
                loadHistoricData(spinContainer, g, qname, range, data, valueRange, ctx, spinner);

                // Reset again to cover edge case where we start from empty but zoomed graph
                // (buggy dygraphs)
                if (data.length > 0) {
                    g.resetZoom();
                }
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

                    var last_y = -Infinity;
                    var i, range, guideline;

                    // LOW LIMITS
                    for (i = defaultAlarm['staticAlarmRange'].length - 1; i >= 0; i--) {
                        range = defaultAlarm['staticAlarmRange'][i];
                        if (range.hasOwnProperty('minInclusive')) {
                            guideline = {
                                y1: last_y,
                                y2: range['minInclusive'],
                                color: colorForLevel(range['level'])
                            };
                            guidelines.push(guideline);
                            last_y = guideline['y2'];
                        }
                    }

                    // HIGH LIMITS
                    last_y = Infinity;
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


        function loadHistoricData(spinContainer, g, qname, range, data, valueRange, ctx, spinner) {
            var now = new Date();
            var nowIso = now.toISOString();
            var before = new Date(now.getTime());
            var beforeIso = nowIso;
            if (range === '15m') {
                before.setMinutes(now.getMinutes() - 15);
                beforeIso = before.toISOString();
            } else if (range === '30m') {
                before.setMinutes(now.getMinutes() - 30);
                beforeIso = before.toISOString();
            } else if (range === '1h') {
                before.setHours(now.getHours() - 1);
                beforeIso = before.toISOString();
            } else if (range === '5h') {
                before.setHours(now.getHours() - 5);
                beforeIso = before.toISOString();
            } else if (range === '1d') {
                before.setDate(now.getDate() - 1);
                beforeIso = before.toISOString();
            } else if (range === '1w') {
                before.setDate(now.getDate() - 7);
                beforeIso = before.toISOString();
            } else if (range === '1m') {
                before.setDate(now.getDate() - 31);
                beforeIso = before.toISOString();
            } else if (range === '3m') {
                before.setDate(now.getDate() - (3*31));
                beforeIso = before.toISOString();
            }

            ctx['archiveFetched'] = false;
            updateGraph(g, 'x\n');
            spinner.spin(spinContainer[0]);

            tmService.getParameterSamples(qname, {
                start: beforeIso.slice(0, -1),
                stop: nowIso.slice(0, -1)
            }).then(function (incomingData) {
                var min, max;
                if (incomingData['sample']) {
                    for (var i = 0; i < incomingData['sample'].length; i++) {
                        var sample = incomingData['sample'][i];
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


                // before updating graph, so 'no data' text is not rendered
                ctx['archiveFetched'] = true;

                if (data.length > 0) {
                    updateGraph(g, data);
                } else {
                    // Ensures that the 'no data' message is shown
                    updateGraph(g, 'x\n');
                }

                spinner.stop();
            });
        }

        function colorForLevel(level) {
            if (level == 'WATCH') return '#f1eee9';
            else if (level == 'WARNING') return '#cddaea';
            else if (level == 'DISTRESS') return '#abc3dd';
            else if (level == 'CRITICAL') return '#769dc4';
            else if (level == 'SEVERE') return '#4280b1';
            else $log.error('Unknown level ' + level);
        }
    }
})();
