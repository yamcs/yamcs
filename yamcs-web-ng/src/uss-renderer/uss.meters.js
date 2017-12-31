USS.Display.createWidgetTypes(['LinearTickMeter']);
$.extend(USS.LinearTickMeter.prototype, {
    parseAndDraw: function (svg, parent, e) {
        var width = this.width;
        var height = this.height;
        
        var cx = width / 2;
        var cy = height / 2;
        
        var $e=$(e);
        var meterMin = this.meterMin = parseFloat($e.children('Minimum').text());
        var meterMax = this.meterMax = parseFloat($e.children('Maximum').text());
        var meterRange = this.meterRange = this.meterMax - this.meterMin;
        
        var orientation = $e.children('Orientation').text().toLowerCase();

        var meterHeight;
        var transform;
        if (orientation == "vertical") {
                transform = {transform: "translate(" + (this.x+15) + ", "+(this.y+10)+")"};
                meterHeight = height - 20;
        } else {
                transform = {transform: "translate(" + (width - 10)  + " " + cy + ") rotate(90)"};
                meterHeight = width - 20;
        }
        this.meterHeight=meterHeight;


        var labelStyle = $e.children('LabelStyle').text().toLowerCase();
        var drawLabels = labelStyle != "no_labels";
        var tickBase = parseFloat($e.children('TickBase').text());
        if(!tickBase) tickBase=0;
        var tickUnit = parseFloat($e.children('TickUnit').text());
        var tickMajorFreq = parseFloat($e.children('TickMajorFrequency').text());
        var tickColor = USS.parseColor($e.children('Color'), 'black');

        //svg.rect(parent, 0, 0, width, height, {fill: 'none', strokeWidth: '1px'});

        var g = svg.group(parent, transform);

        svg.rect(g, -3, 0, 6, meterHeight, {fill: 'white', stroke: 'none'});
        var that = this;
        var tickPainter = function(tick, idx) {
                var settings = {fill: 'none', stroke: tickColor, strokeWidth: '1px'};
                var pos = that.meterHeight - that.getIndicatorHeight(tick);
                // whether to do a major or minor tick
                if (idx % tickMajorFreq == 0) {
                        settings.strokeWidth = '2px';
                        svg.line(g, -8.5, pos, 8.5, pos, settings);
                        if (drawLabels) {
                                var posX = -14;
                                var textSettings = {fontSize: '10'};

                                switch (labelStyle) {
                                case "left_or_top":
                                        posX = -14;
                                        textSettings.textAnchor = 'end';
                                        break;

                                case "right_or_bottom":
                                        posX = 14;
                                        textSettings.textAnchor = 'start';
                                        break;

                                case "alternate_start_left_or_top":
                                        posX = (idx % 4 == 0) ? -14 : 14;
                                        textSettings.textAnchor = (idx % 4 == 0) ? 'end' : 'start';
                                        break;

                                case "alternate_start_right_or_bottom":
                                        posX = (idx % 4 == 0) ? 14 : -14;
                                        textSettings.textAnchor = (idx % 4 == 0) ? 'start' : 'end';
                                        break;
                                }

                                var posY;
                                if (orientation == "horizontal") {
                                        posX = -posX;
                                        if (posX == 14) {
                                                posX = posX + 5;
                                        }
                                        posY = pos;
                                        textSettings.textAnchor = 'middle';
                                        textSettings.transform = "rotate(-90 " + posX + " " + posY + ")";
                                } else {
                                        posY = pos + 3;
                                }

                                svg.text(g, posX, posY, "" + tick, textSettings);
                        }
                } else {
                        svg.line(g, -6, pos, 6, pos, settings);
                }
        };

        var idx = 0;
        var tickStart = tickBase;
        if (tickStart > this.meterMax) {
             tickStart = this.meterMax;
        }
        for (var tick = tickStart; tick >= this.meterMin; tick -= tickUnit, idx++) {
                tickPainter(tick, idx);
        }

        if (idx > 0) {
                idx = 1;
        }
        tickStart = tickBase + tickUnit;
        if (tickStart < this.meterMin) {
                tickStart = this.meterMin;
        }
        for (var tick = tickStart; tick <= this.meterMax; tick += tickUnit, idx++) {
                tickPainter(tick, idx);
        }

        settings = {id: this.id + '-indicator', fill: '#00FF00', stroke: 'black', strokeWidth: '1px'};
        svg.rect(g, -2.5, meterHeight, 6, 0, settings);
    },
    getIndicatorHeight: function(val) {
        return (val + Math.abs(this.meterMin)) / this.meterRange * this.meterHeight;
    },
    updateValue: function(para, usingRaw) {
        var value=USS.getParameterValue(para, usingRaw);
        var pos = this.getIndicatorHeight(value);
        if(pos > this.meterHeight) pos=this.meterHeight;
        var indicator=this.svg.getElementById(this.id+'-indicator');
        indicator.setAttribute('y', this.meterHeight-pos);
        indicator.setAttribute('height', pos);

     }

});

