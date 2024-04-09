FROM node:alpine3.16 AS builder-web-stage

WORKDIR /frontend
COPY package*.json /frontend/
RUN npm install

COPY . /frontend/
RUN npm run build

FROM nginx:alpine AS production-stage
RUN rm -rf /var/www/html/*
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder-web-stage /frontend/dist /var/www/html
RUN chown nginx:nginx /var/www/html

ENTRYPOINT ["nginx", "-g", "daemon off;"]