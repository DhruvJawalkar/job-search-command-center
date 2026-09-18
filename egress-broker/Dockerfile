ARG NODE_RUNTIME_IMAGE=node:22.22-alpine
FROM ${NODE_RUNTIME_IMAGE}
ARG APP_VERSION=dev
ARG VCS_REF=unknown
ARG SOURCE_URL=https://github.com/DhruvJawalkar/job-search-command-center
LABEL org.opencontainers.image.title="Job Search Command Center Egress Broker" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.licenses="LicenseRef-PolyForm-Noncommercial-1.0.0"
WORKDIR /app
COPY package.json server.mjs ./
ENV NODE_ENV=production PORT=8787
USER node
EXPOSE 8787
CMD ["node", "server.mjs"]
