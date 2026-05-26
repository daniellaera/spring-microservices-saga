FROM node:24-slim AS build
WORKDIR /app
COPY shop-ui/package*.json ./
RUN npm ci
COPY shop-ui/ .
RUN npx ng build --configuration=production

FROM nginx:alpine
COPY --from=build /app/dist/shop-ui/browser /usr/share/nginx/html
COPY shop-ui/config/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
