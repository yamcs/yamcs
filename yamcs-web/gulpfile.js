var angularFilesort = require('gulp-angular-filesort'),
    bowerFiles = require('main-bower-files'),
    concat = require('gulp-concat'),
    del = require('del'),
    gulp = require('gulp'),
    gulpFilter = require('gulp-filter'),
    less = require('gulp-less'),
    inject = require('gulp-inject'),
    merge = require('gulp-merge'),
    ngAnnotate = require('gulp-ng-annotate'),
    path = require('path'),
    watch = require('gulp-watch');

gulp.task('clean', function () {
    return del(['build']);
});

// Gathers the main bower files into the build dir
// and concats them in the order defined by main-bower-files
gulp.task('bower-main', ['clean'], function () {
    // Exclude JS because angular-bootstrap only needs bootstrap's css
    var jsFilter = gulpFilter(['**/*.js', '!bootstrap.js'], {restore: true});
    var cssFilter = gulpFilter('**/*.css', {restore: true});
    return gulp.src(bowerFiles())
        .pipe(jsFilter)
        .pipe(concat('vendor.js'))
        .pipe(jsFilter.restore)
        .pipe(cssFilter)
        .pipe(concat('vendor.css'))
        .pipe(cssFilter.restore)
        .pipe(gulpFilter(['*', '!bootstrap.js', '!bootstrap.less', '!**glyphicons**']))
        .pipe(gulp.dest('./build/vendor'));
});

// Copies the referred map files to prevent 404s with an open
// Inspector window (generally not included in bower main files)
gulp.task('bower-map', ['clean'], function () {
    return gulp.src([
        './bower_components/bootstrap/dist/css/bootstrap.css.map',
        './bower_components/dygraphs/dygraph-combined.js.map'
    ]).pipe(gulp.dest('./build/vendor'));
});

gulp.task('bower-fonts', ['clean'], function () {
    return gulp.src([
        './bower_components/bootstrap/dist/fonts/**'
    ]).pipe(gulp.dest('./build/yamcs/fonts'));
});

gulp.task('bower', ['bower-main', 'bower-map', 'bower-fonts']);

gulp.task('css', ['clean'], function () {
    return gulp.src('./src/yamcs/**/*.css')
        .pipe(gulp.dest('./build/yamcs'));
});

gulp.task('less', ['clean'], function () {
    return gulp.src('./src/yamcs/**/*.less')
        .pipe(less())
        .pipe(gulp.dest('./build/yamcs'));
});

gulp.task('js-uss', ['clean'], function () {
    // Order is important for these
    return gulp.src([
            '**/uss.js',
            '**/uss.basic-widgets.js',
            '**/uss.graph.js',
            '**/uss.meters.js',
            '**/jquery.svg.js',
            '**/sprintf-0.7-beta1.js'
            //'**/highcharts.src.js'
        ])
        .pipe(concat('uss.js'))
        .pipe(gulp.dest('./build/yamcs/uss'));
});

gulp.task('js', ['clean', 'js-uss'], function () {
    return gulp.src(['./src/yamcs/**/*.js', '!./src/yamcs/**/uss/*'])
        .pipe(ngAnnotate())
        .pipe(gulp.dest('./build/yamcs'));
});

gulp.task('html', ['clean'], function () {
    return gulp.src('./src/yamcs/**/*.html')
        .pipe(gulp.dest('./build/yamcs'));
});

gulp.task('img', ['clean'], function () {
    return gulp.src('./src/yamcs/**/*.png')
        .pipe(gulp.dest('./build/yamcs'));
});

// Updates the CSS and JS references defined in the root index.html
gulp.task('index', ['clean', 'bower', 'css', 'less', 'js', 'html', 'img'], function () {
    return gulp.src('./src/index.html')
        .pipe(inject(gulp.src('./build/vendor/**/*', {read: false}),
            {ignorePath: '/build', addPrefix: '/_static', name: 'bower'}))
        .pipe(inject(gulp.src('./build/yamcs/**/*.js', {}).pipe(angularFilesort()),
            {ignorePath: '/build', addPrefix: '/_static'}))
        .pipe(inject(gulp.src('./build/yamcs/**/*.css',{read: false}),
            {ignorePath: '/build', addPrefix: '/_static'}))
        .pipe(gulp.dest('./build'));
});

/**
 *
 * Default 'gulp' behaviour
 *
 */
gulp.task('default', ['css', 'js', 'index']);

