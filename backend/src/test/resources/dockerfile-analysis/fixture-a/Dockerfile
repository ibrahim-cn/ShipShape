FROM node:20-alpine
WORKDIR /app
COPY package.json .
USER node
HEALTHCHECK CMD true
CMD ["node", "server.js"]
