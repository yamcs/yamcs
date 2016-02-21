(function() {
    'use strict';

    /**
     * Plot directive using dygraphs.
     *
     * Converts, holds and updates data and exposes control methods for use in a controller.
     * However it does NOT send out any data requests itself, instead relying on callbacks
     * that are sent back to a controller (e.g. zoom event).
     *
     * While this plot directive is currently used only in one controller, this explicit separation
     * is intended so that its use could eventually be extended to other places.
     */
    angular.module('yamcs.core').directive('yPlot', yPlot);

    /* @ngInject */
    function yPlot($log, configService) {

        return {
            restrict: 'EA',
            scope: {
                pinfo: '=',
                rangeSamples: '=',
                alarms: '=',
                control: '=', // Optionally allows controlling this directive from the outside
                onZoom: '&'
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
                var g = makePlot(plotEl[0], scope, model);
                g.ready(function () {
                    scope.$watch(attrs.rangeSamples, function (rangeSamples) {
                        var rangeData = convertSampleDataToDygraphs(rangeSamples);
                        updateModel(rangeData);
                        updateGraph(g, rangeData, model);
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
                scope.__control.repaint = function() {
                    updateModel(scope.rangeSamples);
                    updateGraph(g, scope.rangeSamples, model);
                };
                scope.__control.initialized = true;

                function updateModel(pdata) {
                    var match = false;
                    for (var i = 0; i < pdata.length; i++) {
                        if (pdata[i]['points'].length > 0) {
                            match = true;
                            break;
                        }
                    }
                    model.hasData = match;
                }
            }
        };

        function makePlot(containingDiv, scope, model) {
            model.valueRange = calculateInitialPlotRange(scope.pinfo);
            var guidelines = calculateGuidelines(scope.pinfo);
            var label = scope.pinfo['qualifiedName'];

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
                panEdgeFraction: 0,
                labels: ['Generation Time', label],
                labelsDiv: 'parameter-detail-legend',
                valueRange: model.valueRange,
                yRangePad: 10,
                axes: {
                    y: { axisLabelWidth: 50 }
                },
                rightGap: 0,
                labelsUTC: configService.get('utcOnly', false),
                zoomCallback: function(minDate, maxDate) {
                    // Report to controller
                    scope.onZoom({
                        startDate: new Date(minDate),
                        stopDate: new Date(maxDate)
                    });
                },
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
            if (pdata.length === 0 || pdata[0]['points'].length === 0) {
                g.updateOptions({ file: 'x\n' });
            } else {
                updateValueRange(g, model.valueRange, pdata[0]['min'], pdata[0]['max']);
                g.updateOptions({
                    file: pdata[0]['points'],
                    drawPoints: pdata[0]['points'].length < 50
                });

                /*g.setAnnotations([{
                     series: "/YSS/SIMULATOR/BatteryVoltage2",
                     x: Date.parse("2015-11-26T18:50:00"),
                     shortText: "W",
                     text: "Coldest Day"
                }]);*/ // TODO date needs to match exactly an existing point :/ there seems to be a method findClosestRow
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

    /**
     * Converts the REST sample data to native dygraphs format
     * http://dygraphs.com/data.html#array
     */
    function convertSampleDataToDygraphs(incomingData) {
        var rangeMin, rangeMax;
        var points = [];
        if (incomingData['sample']) {
            for (var i = 0; i < incomingData['sample'].length; i++) {
                var sample = incomingData['sample'][i];
                var t = new Date();
                t.setTime(Date.parse(sample['time']));
                var v = sample['avg'];
                var min = sample['min'];
                var max = sample['max'];

                if (typeof rangeMin === 'undefined') {
                    rangeMin = min;
                    rangeMax = max;
                } else {
                    if (rangeMin > min) rangeMin = min;
                    if (rangeMax < max) rangeMax = max;
                }
                points.push([t, [min, v, max]]);
            }
        }

        return [{
            //name: qname,
            points: points,
            min: rangeMin,
            max: rangeMax
        }];
    }
})();
