# Web Dev

Start the Angular web dev server for the YAMCS web UI with hot reload.

## Usage

`/web-dev` — start the dev server (proxies API calls to localhost:8090)  
`/web-dev --full` — rebuild webapp-sdk first, then start the dev server  

## Prerequisites

- Node.js and npm must be installed
- A YAMCS backend should be running at http://localhost:8090 (use `/run-pus` to start one)

## Steps

Working directory: `/Users/nikhil/GSS Workspace/yamcs-Pixxel-fork/yamcs-web/src/main/webapp`

1. If `--full` is given, first run `npm run build-lib` to rebuild the webapp-sdk library.
2. Start the Angular dev server:
   ```
   npx ng serve webapp --proxy-config proxy.dev.json
   ```
3. The dev server will be available at http://localhost:4200 with live reload.
4. Report the URL once the server is ready, or any startup errors.

## Notes

- `proxy.dev.json` proxies `/api` and `/auth` requests to the YAMCS backend at localhost:8090
- The full production build uses `npm run build` followed by `mvn install -DskipTests -pl yamcs-web -am`