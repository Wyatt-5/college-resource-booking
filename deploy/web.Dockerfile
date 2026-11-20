FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY frontend/ /usr/share/nginx/html/
USER 101:101
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
