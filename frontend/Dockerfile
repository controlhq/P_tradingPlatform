# ---- build stage: Node 20 ----
FROM node:20-alpine AS build
WORKDIR /build

# dependencies first, so this layer is reused until the lockfile changes
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# empty = same origin: the browser calls /api on nginx, which proxies it to the backend
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---- runtime stage: static files served by nginx ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
