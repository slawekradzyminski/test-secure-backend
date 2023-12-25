var webpack = require('webpack');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = {
    mode: 'development',
    resolve: {
        extensions: ['.js', '.jsx', '.ts', '.tsx']
    },
    module: {
        rules: [
            {
                test: /\.jsx?$/,
                loader: 'babel-loader'
            },
            {
                test: /\.tsx?$/,
                loader: 'babel-loader'
            }
        ]
    },
    plugins: [
        new HtmlWebpackPlugin({
            template: './src/index.html',
            favicon: './src/favicon.ico'
        }),
        new webpack.DefinePlugin({
            'process.env': {
                'API_URL': JSON.stringify('http://localhost:4001')
            }
        }),
        new CopyWebpackPlugin({
            patterns: [
                { from: './src/favicon.ico', to: 'favicon.ico' }
            ]
        })
    ],
    devServer: {
        historyApiFallback: true
    },
    externals: {
        config: JSON.stringify({
            apiUrl: 'http://localhost:4001'
        })
    }
}