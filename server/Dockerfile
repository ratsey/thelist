FROM node:24-alpine

WORKDIR /app
COPY package.json index.js ./

ENV PORT=8080
ENV DB_PATH=/data/thelist-sync.db

RUN mkdir -p /data
VOLUME ["/data"]
EXPOSE 8080

CMD ["node", "index.js"]
