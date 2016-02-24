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

    // Dygraphs does not have a nice hook that gets called on pan end (other than hacking it in onDrawCallback)
    // So modify their code...
    var origEndPan = Dygraph.Interaction.endPan;
    Dygraph.Interaction.endPan = function(event, g, context) {
        origEndPan(event, g, context);
        if (g.getFunctionOption('yamcs_panCallback')) {
            var xAxisRange = g.xAxisRange();
            var x1 = new Date(xAxisRange[0]);
            var x2 = new Date(xAxisRange[1]);
            g.getFunctionOption('yamcs_panCallback').call(g, x1, x2, g.yAxisRanges());
        }
    };
    Dygraph.endPan = Dygraph.Interaction.endPan;

    /* @ngInject */
    function yPlot($log, $interval, $filter, configService) {

        return {
            restrict: 'EA',
            scope: {
                pinfo: '=',
                samples: '=', // The complete set of samples (does not include zoom detail)
                alarms: '=',
                control: '=', // Optionally allows controlling this directive from the outside
                onZoom: '&',
                refresh: '@' // Interval at which to refresh the plot
            },
            link: function(scope, element, attrs) {
                var plotWrapper = angular.element('<div>');
                var spinContainer = angular.element('<div style="position: absolute; top: 50%; left: 50%;"></div>');
                plotWrapper.prepend(spinContainer);

                var plotEl = angular.element('<div style="width: 100%;"></div>');
                plotWrapper.prepend(plotEl);

                element.prepend(plotWrapper);

                // Dict holding latest averaged realtime (polled by refresher)
                var pendingRealtime;

                var model = {
                    hasData: false,
                    valueRange: [null, null],
                    spinning: false,
                    allPoints: [],
                    splicedPoints: [], // when the range is combined with a detail range
                    min_y: undefined,
                    max_y: undefined,
                    isRangeSelectorMouseDown: false
                };
                var g = makePlot(plotEl[0], scope, model);
                g.ready(function () {

                    /**
                     * Loads or reloads the full-range of data of the plot as provided
                     * by the controller.
                     */
                    scope.$watch(attrs.samples, function (samples) {
                        pendingRealtime = null;
                        if (samples) {
                            var pointData = convertSampleDataToDygraphs(samples);
                            model.allPoints = pointData[0].points;
                            model.splicedPoints = model.allPoints;
                            model.min_y = pointData[0].min;
                            model.max_y = pointData[0].max;
                        } else {
                            if (model.hasData) {
                                g.resetZoom();
                            }
                            model.allPoints = [];
                            model.splicedPoints = [];
                        }

                        model.hasData = model.allPoints.length > 0;
                        updateGraph(g, model);
                    });

                    /**
                     * Dygraphs does not have a nice hook for range selector, so force it
                     */
                    var rangeEl = plotEl.find('.dygraph-rangesel-fgcanvas, .dygraph-rangesel-zoomhandle');
                    var windowEl = $(window);
                    rangeEl.on('mousedown.yamcs touchstart.yamcs', function(evt) {
                        model.isRangeSelectorMouseDown = true;

                        // On up, load new detail data
                        windowEl.off('mouseup.yamcs touchend.yamcs');
                        windowEl.on('mouseup.yamcs touchend.yamcs', function(evt) {
                            windowEl.off('mouseup.yamcs touchend.yamcs');
                            model.isRangeSelectorMouseDown = false;
                            var xAxisRange = g.xAxisRange();
                            scope.onZoom({
                                startDate: new Date(xAxisRange[0]),
                                stopDate: new Date(xAxisRange[1])
                            });
                        });
                    });
                });

                // Refreshes if there are new realtime points
                var refresher = $interval(refreshPlot, attrs.refresh || 5000);
                scope.$on('$destroy', function() {
                    $interval.cancel(refresher);
                });

                scope.__control = scope.control || {};
                var spinner = new Spinner({color: '#ccc'});
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
                scope.__control.repaint = function() {
                    updateGraph(g, model);
                };
                scope.__control.toggleGrid = function () {
                    if (g.getOption('drawGrid')) {
                        g.updateOptions({ drawGrid: false });
                    } else {
                        g.updateOptions({ drawGrid: true });
                    }
                };

                /**
                 * Averages incoming realtime. Picked up by the refresher at
                 * regular intervals.
                 */
                scope.__control.appendPoint = function(pval) {
                    if (pval && pval.hasOwnProperty('engValue')) {
                        var t = Date.parse(pval['generationTimeUTC']);
                        var val = $filter('stringValue')(pval);

                        if (pendingRealtime) {
                            var n = ++pendingRealtime['n'];
                            pendingRealtime['avgt'] -= pendingRealtime['avgt'] / n;
                            pendingRealtime['avgt'] += t / n;
                            pendingRealtime['avg'] -= pendingRealtime['avg'] / n;
                            pendingRealtime['avg'] += val / n;
                            pendingRealtime['min'] = Math.min(val, pendingRealtime['min']);
                            pendingRealtime['max'] = Math.max(val, pendingRealtime['max']);
                        } else {
                            pendingRealtime = {
                                avgt: t,
                                avg: val,
                                min: val,
                                max: val,
                                n: 1
                            }
                        }
                    }
                };

                /**
                 * Loads a subset of data for plot. Useful for resampling
                 * zoomed ranges. Currently discards any previously loaded detail
                 * in favour of new detail.
                 */
                scope.__control.spliceDetailSamples = function(detailSamples) {
                    if (!detailSamples) {
                        return;
                    }

                    // [ [t, [min, v, max]], [t, [min, v, max]], ...  ]
                    var allPoints = model.allPoints;
                    var detailPoints = convertSampleDataToDygraphs(detailSamples)[0].points;

                    if (detailPoints.length) {
                        var dt0 = detailPoints[0];
                        var dtn = detailPoints[detailPoints.length - 1];

                        // Search insert position by comparing on 't'
                        var insertStartIdx = _.sortedIndexBy(allPoints, dt0, function(v) { return v[0]; });
                        var insertStopIdx = _.sortedLastIndexBy(allPoints, dtn, function(v) { return v[0]; });

                        // Spliced
                        model.splicedPoints = [];
                        Array.prototype.push.apply(model.splicedPoints, allPoints.slice(0, insertStartIdx));
                        Array.prototype.push.apply(model.splicedPoints, detailPoints);
                        Array.prototype.push.apply(model.splicedPoints, allPoints.slice(insertStopIdx));

                        updateGraph(g, model);
                    }
                };

                scope.__control.initialized = true;

                function refreshPlot() {
                    if (!pendingRealtime) {
                        return;
                    }

                    var t = new Date();
                    t.setTime(pendingRealtime['avgt']);
                    var dypoint = [
                        t, [ pendingRealtime['min'], pendingRealtime['avg'], pendingRealtime['max']]
                    ];

                    //console.log('refresh plot from ' + pendingRealtime['n'] + ' points ... ', dypoint);
                    if (model.hasData) {
                        model.min_y = Math.min(model.min_y, dypoint[1][0]);
                        model.max_y = Math.max(model.max_y, dypoint[1][2]);
                    } else {
                        model.min_y = dypoint[1][0];
                        model.max_y = dypoint[1][2];
                    }
                    model.allPoints.push(dypoint);
                    if (model.splicedPoints.length > 0) {
                        model.splicedPoints.push(dypoint);
                    }
                    model.hasData = true;
                    pendingRealtime = null;
                    updateGraph(g, model);
                }
            }
        };

        function makePlot(containingDiv, scope, model) {
            model.valueRange = calculateInitialPlotRange(scope.pinfo);
            var guidelines = calculateGuidelines(scope.pinfo);
            var label = scope.pinfo['qualifiedName'];

            return new Dygraph(containingDiv, 'X\n', {
                legend: 'always',
                drawGrid: false,
                drawPoints: true,
                showRoller: false,
                customBars: true,
                strokeWidth: 2,
                gridLineColor: '#444',
                axisLineColor: '#333',
                axisLabelColor: '#666',
                axisLabelFontSize: 11,
                digitsAfterDecimal: 6,
                //panEdgeFraction: 0,
                labels: ['Generation Time', label],
                labelsDiv: 'parameter-detail-legend',
                showRangeSelector: true,
                rangeSelectorPlotStrokeColor: '#333',
                rangeSelectorPlotFillColor: '#008080',
                valueRange: model.valueRange,
                yRangePad: 10,
                axes: {
                    y: { axisLabelWidth: 50 }
                },
                rightGap: 0,
                labelsUTC: configService.get('utcOnly', false),
                zoomCallback: function(minDate, maxDate) {
                    // Dragging range handles causes many-many zoomCallbacks
                    if (!model.isRangeSelectorMouseDown) {
                        scope.onZoom({ // Report to controller
                            startDate: new Date(minDate),
                            stopDate: new Date(maxDate)
                        });
                    }
                },
                yamcs_panCallback: function(minDate, maxDate) {
                    // Dragging range handles causes many-many zoomCallbacks
                    if (!model.isRangeSelectorMouseDown) {
                        scope.onZoom({ // Report to controller
                            startDate: new Date(minDate),
                            stopDate: new Date(maxDate)
                        });
                    }
                },
                drawHighlightPointCallback: function(g, seriesName, ctx, cx, cy, color, radius) {
                    ctx.beginPath();
                    ctx.fillStyle = '#ffffff';
                    ctx.arc(cx, cy, radius, 0, 2 * Math.PI, false);
                    ctx.fill();
                },
                underlayCallback: function(canvasCtx, area, g) {
                    var prevAlpha = canvasCtx.globalAlpha;
                    canvasCtx.globalAlpha = 0.4;

                    /*if (!model.hasData && !model.spinning) {
                        canvasCtx.font = '20px Verdana';
                        canvasCtx.textAlign = 'center';
                        canvasCtx.textBaseline = 'middle';
                        var textX = area.x + (area.w / 2);
                        var textY = area.y + (area.h / 2);
                        canvasCtx.fillText('no data for this range', textX, textY);
                    }*/

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
            if (pinfo && pinfo.hasOwnProperty('type') && pinfo['type'].hasOwnProperty('defaultAlarm')) {
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

        function updateGraph(g, model) {
            if (model.splicedPoints.length === 0) {
                g.updateOptions({ file: 'x\n' });
            } else {
                updateValueRange(g, model.valueRange, model.min_y, model.max_y);
                g.updateOptions({
                    file: model.splicedPoints,
                    drawPoints: model.splicedPoints.length < 50
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
