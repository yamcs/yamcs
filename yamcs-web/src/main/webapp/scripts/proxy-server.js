#!/usr/bin/env node

// Simple reverse proxy for test purposes.

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();

const wsProxy = createProxyMiddleware({
  target: 'ws://127.0.0.1:8090',
  changeOrigin: true,
  headers: {
    'X-Remote-User': 'admin',
  }
});

app.use('/', wsProxy);

const server = app.listen(5050);
server.on('upgrade', wsProxy.upgrade);
