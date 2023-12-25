FROM node:lts-alpine
WORKDIR /app
ENV PATH /app/node_modules/.bin:$PATH
COPY package.json .
COPY package-lock.json .
RUN npm install
COPY ../../test-secure-frontend ./
CMD [ "npm", "start" ]