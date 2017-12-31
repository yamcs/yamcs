USS.Display.createWidgetTypes(['LineGraph']);
$.extend(USS.LineGraph.prototype, {
    parseAndDraw: function (svg, parent, e) {
        svg.group(parent, this.id, {transform: "translate("+this.x+","+this.y+")"});
        var $e=$(e);
        var type=$e.children('Type').text();
        var title =$e.children('Title').text();
        var labelsStyle= {style: {fontFamily: 'sans-serif', fontSize: '10px'}};

        var titleStyle=USS.parseTextStyle($e.children('TitleTextStyle'));

        var settings={
             chart: { renderTo: this.id, type: 'line', width: this.width, height:this.height,
                      animation: false, clientZoom: false,  spacingTop: 0, spacingBottom: 5},
             legend: {enabled: false},
             credits: {enabled: false},
             title: { text: title, style: titleStyle },
             plotOptions: {
                 line: {
                     color:'black', 
                     animation: false, 
                     shadow:false, 
                     marker: {enabled: false},
                     enableMouseTracking: false
                 }, 
                 series:{lineWidth:1}
             }
       };
       settings.xAxis=this.parseDomainAxis($e.children('DefaultDomainAxis'));
       settings.yAxis=this.parseRangeAxis($e.children('DefaultRangeAxis'));
       settings.series= [{
                id: 'series-1',
                name: this.dataBindings[0].parameterName,
                data: []
              } ];
      this.chart = new Highcharts.Chart(settings);
    },

    parseRangeAxis: function(e) {
        var $e = $(e);
        var titleStyle = {fontFamily: 'sans-serif', fontSize: '12px', fontWeight: 'normal'};
        var labelStyle = {fontFamily: 'sans-serif', fontSize: '10px', fontWeight: 'normal'};

        labelStyle.color = titleStyle.color = USS.parseColor($e.children('AxisColor')[0], 'black');
        var yaxis = {lineWidth:1, lineColor:labelStyle.color, gridLineDashStyle: 'dash', 
                     labels: {style: labelStyle}, startOnTick: false, 
                     tickPixelInterval:20, endOnTick: false};

        var label = $e.children('Label').text();
        yaxis.title = {text: label, style: titleStyle};

        var autoRange = ($e.children('AutoRange').text().toLowerCase()=='true');
        this.yAutoRange = autoRange;
        if(!autoRange) {
            var axisRange=$e.children('AxisRange')[0];
            var min = $(axisRange).children('Lower').text();
            if(min) yaxis.min=parseFloat(min);
            var max = $(axisRange).children('Upper').text();
            if(max) yaxis.max=parseFloat(max);
        }
        var stickyZero = ($e.children('StickyZero').text().toLowerCase()=='true');
        if(stickyZero) yaxis.min=0;
        return yaxis;
    },

    parseDomainAxis: function(e) {
        var titleStyle = {fontFamily: 'sans-serif', fontSize: '12px', fontWeight: 'normal'};
        var labelStyle = {fontFamily: 'sans-serif', fontSize: '12px', fontWeight: 'normal'};
        var $e=$(e);        
        var type=$e.children('AxisMode').text().toLowerCase();
        if(type != 'time_based_absolute') {
            console.log("TODO xaxis of type ", type);
            return null;
        }
        labelStyle.color= titleStyle.color = USS.parseColor($e.children('AxisColor')[0], 'black');
        var xaxis = {type:'datetime', gridLineDashStyle: 'dash', 
                    labels: {style: labelStyle}, startOnTick: false, gridLineWidth:1};

        var label = $e.children('Label').text();
        xaxis.title = {text: label, style: titleStyle};

        var autoRange = ($e.children('AutoRange').text().toLowerCase()=='true');
        this.xAutoRange = autoRange;
        if(!autoRange) {
            var axisRange=$e.children('AxisRange')[0];
            xaxis.min = parseFloat($(axisRange).children('Lower').text());
            xaxis.max = parseFloat($(axisRange).children('Upper').text());
            this.xRange = xaxis.max - xaxis.min;
        }
        return xaxis;
    },
    updateValue: function(para, usingRaw) {
        var series=this.chart.get('series-1');
        var value=USS.getParameterValue(para, usingRaw);
        var t=para.generationTime;
        var xaxis = series.xAxis;
        if(!this.xAutoRange) {
            var extr = xaxis.getExtremes();
            if(extr.max < t) {
                var s = this.xRange/3;
                xaxis.setExtremes(t+s-this.xRange, t+s); 
            }
        }
        series.addPoint([t,value]);
     }
});

