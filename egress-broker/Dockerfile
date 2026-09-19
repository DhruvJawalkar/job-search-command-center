ARG NODE_RUNTIME_IMAGE=node:22.23.2-alpine3.24@sha256:b6f26b36c8ff49624cfdac716b8ea1138d606df02586a77d364bb5536a634f85
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
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack /opt/yarn-v1.22.22 \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
      /usr/local/bin/pnpm /usr/local/bin/pnpx /usr/local/bin/yarn /usr/local/bin/yarnpkg
COPY package.json server.mjs ./
ENV NODE_ENV=production PORT=8787
USER node
EXPOSE 8787
CMD ["node", "server.mjs"]
