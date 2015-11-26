(function() {
    'use strict';

    angular
        .module('yamcs.core')
        .directive('yPlot', yPlot);

    /* @ngInject */
    function yPlot($log, configService) {

        return {
            restrict: 'EA',
            scope: {
                pinfo: '=',
                pdata: '=',
                alarms: '=',
                control: '=' // Optionally allows controlling this directive from the outside
            },
            link: function(scope, element, attrs) {
                var plotWrapper = angular.element('<div>');
                var spinContainer = angular.element('<div style="position: absolute; top: 50%; left: 50%;"></div>');
                plotWrapper.prepend(spinContainer);

                var plotEl = angular.element('<div style="width: 100%;"></div>');
                plotWrapper.prepend(plotEl);

                element.prepend(plotWrapper);

                var model = {
                    hasData: false,
                    valueRange: [null, null],
                    spinning: false
                };
                var g = makePlot(plotEl[0], scope.pinfo, model);
                g.ready(function () {
                    scope.$watch(attrs.pdata, function (pdata) {
                        var match = false;
                        for (var i = 0; i < pdata.length; i++) {
                            if (pdata[i]['points'].length > 0) {
                                match = true;
                                break;
                            }
                        }
                        model.hasData = match;
                        updateGraph(g, pdata, model);
                    });
                });

                scope.__control = scope.control || {};
                var spinner = new Spinner();
                scope.__control.startSpinner = function() {
                    if (!model.spinning) {
                        model.spinning = true;
                        spinner.spin(spinContainer[0]);
                    }
                };
                scope.__control.stopSpinner = function () {
                    model.spinning = false;
                    spinner.stop();
                };
                scope.__control.emptyPlot = function () {
                    model.hasData = false;
                    updateGraph(g, [], model);
                };
                scope.__control.resetZoom = function () {
                    // dygraphs gives errors when resetting with empty plot,
                    // so work around that
                    if (hasData) {
                        g.resetZoom();
                    }
                };
                scope.__control.initialized = true;

                /*scope.$watch('range', function(range) {
                    var qname = pinfo['qualifiedName'];

                    if (data.length > 0) {
                        g.resetZoom();
                    }

                    valueRange = calculateInitialPlotRange(pinfo);
                    data.length = 0;

                    // Add new set of data
                    loadHistoricData(spinContainer, g, qname, range, data, valueRange, ctx, spinner);

                    // Reset again to cover edge case where we start from empty but zoomed graph
                    // (buggy dygraphs)
                    if (data.length > 0) {
                        g.resetZoom();
                    }
                });*/
            }
        };

        function makePlot(containingDiv, pinfo, model) {
            model.valueRange = calculateInitialPlotRange(pinfo);
            var guidelines = calculateGuidelines(pinfo);
            var label = pinfo['qualifiedName'];

            return new Dygraph(containingDiv, 'X\n', {
                legend: 'always',
                drawPoints: true,
                showRoller: false,
                customBars: true,
                animatedZooms: true,
                gridLineColor: 'lightgray',
                axisLabelColor: '#666',
                axisLabelFontSize: 11,
                digitsAfterDecimal: 6,
                labels: ['Generation Time', label],
                labelsDiv: 'parameter-detail-legend',
                valueRange: model.valueRange,
                yRangePad: 10,
                axes: {
                    y: { axisLabelWidth: 50 }
                },
                rightGap: 0,
                labelsUTC: configService.get('utcOnly', false),
                underlayCallback: function(canvasCtx, area, g) {
                    var prevAlpha = canvasCtx.globalAlpha;
                    canvasCtx.globalAlpha = 0.4;

                    if (!model.hasData && !model.spinning) {
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
                            min = (min === null) ? range['minInclusive'] : Math.min(range['minInclusive'], min);
                            max = (max === null) ? range['minInclusive'] : Math.max(range['minInclusive'], max);
                        }
                        if (range.hasOwnProperty('maxInclusive')) {
                            min = (min === null) ? range['maxInclusive'] : Math.min(range['maxInclusive'], min);
                            max = (max === null) ? range['maxInclusive'] : Math.max(range['maxInclusive'], max);
                        }
                    });
                }
            }

            // Hmm null appears to be interpreted as 0...
            if (min === null) min = max;
            if (max === null) max = min;
            return [min, max];
        }

        function colorForLevel(level) {
            if (level == 'WATCH') return '#ffdddb';
            else if (level == 'WARNING') return '#ffc3c1';
            else if (level == 'DISTRESS') return '#ffaaa8';
            else if (level == 'CRITICAL') return '#c35e5c';
            else if (level == 'SEVERE') return '#a94442';
            else $log.error('Unknown level ' + level);
        }

        function updateGraph(g, pdata, model) {
            console.log('updating graph');
            if (pdata.length === 0 || pdata[0]['points'].length === 0) {
                g.updateOptions({ file: 'x\n' });
            } else {
                updateValueRange(g, model.valueRange, pdata[0]['min'], pdata[0]['max']);
                g.updateOptions({
                    file: pdata[0]['points'],
                    drawPoints: pdata[0]['points'].length < 50
                });
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
    }
})();
