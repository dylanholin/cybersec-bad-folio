FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Certificat auto-signé pour le développement HTTPS
RUN mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 365 \
      -subj "/CN=localhost" \
      -newkey rsa:2048 \
      -keyout /etc/nginx/ssl/devfolio.key \
      -out /etc/nginx/ssl/devfolio.crt

EXPOSE 80 443

RUN chown -R nginx:nginx /etc/nginx/ssl
USER nginx

CMD ["nginx", "-g", "daemon off;"]
