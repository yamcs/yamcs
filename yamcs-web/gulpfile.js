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
    rename = require('gulp-rename'),
    replace = require('gulp-replace'),
    sourcemaps = require('gulp-sourcemaps'),
    uglify = require('gulp-uglify'),
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
            .pipe(sourcemaps.init())
                .pipe(concat('vendor.js'))
                // Ugh, this replace because dygraphs doesn't make pan overlay configurable
                .pipe(replace('rgba(240, 240, 240, 0.6)', 'rgba(0, 0, 0, 0.4)'))
                //.pipe(uglify()) // TODO make conditional because slow
            .pipe(sourcemaps.write())
        .pipe(jsFilter.restore)
        .pipe(cssFilter)
            .pipe(sourcemaps.init())
                .pipe(concat('vendor.css'))
            .pipe(sourcemaps.write())
        .pipe(cssFilter.restore)
        .pipe(gulpFilter(['*', '!bootstrap.js', '!bootstrap.less', '!**glyphicons**']))
        .pipe(gulp.dest('./build/_site'));
});

// Copies the referred map files to prevent 404s with an open
// Inspector window (generally not included in bower main files)
gulp.task('bower-map', ['clean'], function () {
    return gulp.src([
        './bower_components/bootstrap/dist/css/bootstrap.css.map',
        './bower_components/dygraphs/dygraph-combined.js.map'
    ]).pipe(gulp.dest('./build/_site'));
});

gulp.task('bower-fonts', ['clean'], function () {
    return gulp.src([
        './bower_components/bootstrap/dist/fonts/**'
    ]).pipe(gulp.dest('./build/_site/fonts'));
});

gulp.task('bower', ['bower-main', 'bower-map', 'bower-fonts']);

gulp.task('css', ['clean'], function () {
    return gulp.src('./src/**/*.css')
        .pipe(gulp.dest('./build/_site'));
});

gulp.task('less', ['clean'], function () {
    return gulp.src('./src/**/*.less')
        .pipe(less())
        .pipe(gulp.dest('./build/_site'));
});

gulp.task('js-uss', ['clean'], function () {
    // Order is important for these
    return gulp.src([
            '**/lib/uss.js',
            '**/lib/uss.basic-widgets.js',
            '**/lib/uss.graph.js',
            '**/lib/uss.meters.js',
            '**/lib/jquery.svg.js',
            '**/lib/sprintf-0.7-beta1.js'
            //'**/lib/highcharts.src.js'
        ])
        .pipe(concat('uss-lib.js'))
        .pipe(gulp.dest('./build/_site/uss'));
});

gulp.task('js', ['clean', 'js-uss'], function () {
    return gulp.src(['./src/**/*.js', '!./src/**/uss/lib/*'])
        .pipe(ngAnnotate())
        .pipe(angularFilesort())
        .pipe(concat('yamcs-web.js'))
        .pipe(gulp.dest('./build/_site'));
});

gulp.task('html', ['clean'], function () {
    return gulp.src(['./src/**/*.html', '!./src/*.html'])
        .pipe(gulp.dest('./build/_site'));
});

gulp.task('img', ['clean'], function () {
    return gulp.src(['./src/**/*.png', './src/**/*.ico'])
        .pipe(gulp.dest('./build/_site'));
});

gulp.task('config', ['clean'], function () {
    return gulp.src('./src/config.json')
        .pipe(rename('config.json.sample'))
        .pipe(gulp.dest('./build'));
});

// Updates the CSS and JS references defined in the root html files
gulp.task('index', ['clean', 'bower', 'css', 'less', 'js'], function () {
    return gulp.src('./src/*.html')
        .pipe(inject(gulp.src(['./build/_site/vendor.js', './build/_site/uss/uss-lib.js'], {read: false}),
            {ignorePath: '/build', addPrefix: '/_static', name: 'bower'}))
        .pipe(inject(gulp.src('./build/_site/yamcs-web.js', {}),
            {ignorePath: '/build', addPrefix: '/_static'}))
        .pipe(inject(gulp.src('./build/_site/**/*.css',{read: false}),
            {ignorePath: '/build', addPrefix: '/_static'}))
        .pipe(gulp.dest('./build/_site'));
});

/**
 *
 * Default 'gulp' behaviour
 *
 */
gulp.task('default', ['css', 'js', 'html', 'img', 'config', 'index']);
