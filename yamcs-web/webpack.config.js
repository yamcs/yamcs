var path = require('path')
var webpack = require('webpack')
var UglifyJsPlugin = webpack.optimize.UglifyJsPlugin
var env = process.env.WEBPACK_ENV
var libraryName = 'yamcs-web'
var plugins = []
var outputFile
if (env === 'build') {
    plugins.push(new UglifyJsPlugin({ minimize: true }))
    outputFile = libraryName + '.min.js'
} else {
    outputFile = libraryName + '.js'
}


module.exports = {
    entry: [
        './src/index.js'
    ],
    output: {
        path: path.join(__dirname, '/build2'),
        filename: outputFile,
        library: libraryName,
        libraryTarget: 'var'
    },
    externals: {
        'moment': 'moment',
        'moment-timezone': 'moment'
    },
    module: {
        loaders: [
            { test: /\.js$/, loader: 'babel', exclude: /node_modules/ },
            { test: /\.js$/, loader: 'eslint-loader', exclude: /node_modules/ },
            { test: /\.s?css$/, loader: 'style!css!sass' },
            { test: /\.json$/, loader: 'json' }
        ]
    },
    resolve: {
        extensions: ['', '.css', '.json', '.js']
    },
    plugins: plugins
}
