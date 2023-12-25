## Local run

```commandline
set NODE_TLS_REJECT_UNAUTHORIZED=0
npm install
npm start
```

## Docker local run

Change in package.json localhost to 0.0.0.0

```commandline
"start": "webpack-dev-server --host 0.0.0.0 --open --port 8081"
```

Build docker image

```commandline
docker build --tag=frontend:latest .
docker run -p8081:8081 frontend:latest
```

## Cypress config
```
/Users/ocado/Library/Caches/Cypress/9.2.0/Cypress.app/Contents/Resources/app/packages/server/config
```

## Docker remote run (warning: may be outdated)

```commandline
docker run -p8081:8081 slawekradzyminski/frontend:latest
```

## Verification

Application should run on [http://localhost:8081](http://localhost:8081)