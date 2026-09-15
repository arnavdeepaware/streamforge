FROM node:22-alpine AS build

WORKDIR /workspace
COPY web-dashboard/package.json web-dashboard/package-lock.json ./
RUN npm ci
COPY web-dashboard ./
RUN npm run build

FROM nginx:1.27-alpine
COPY web-dashboard/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 5173
