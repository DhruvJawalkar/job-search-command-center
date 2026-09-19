ARG NODE_BUILD_IMAGE=node:22.23.2-alpine3.24@sha256:b6f26b36c8ff49624cfdac716b8ea1138d606df02586a77d364bb5536a634f85
ARG NODE_RUNTIME_IMAGE=node:22.23.2-alpine3.24@sha256:b6f26b36c8ff49624cfdac716b8ea1138d606df02586a77d364bb5536a634f85

FROM ${NODE_BUILD_IMAGE} AS build
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN pnpm run build

FROM build AS deploy
RUN pnpm --filter job-search-command-center-portal deploy --prod --legacy /runtime

FROM ${NODE_RUNTIME_IMAGE}
ARG APP_VERSION=dev
ARG VCS_REF=unknown
ARG SOURCE_URL=https://github.com/DhruvJawalkar/job-search-command-center
LABEL org.opencontainers.image.title="Job Search Command Center Portal" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.licenses="LicenseRef-PolyForm-Noncommercial-1.0.0" \
      dev.dhruv.job-search-command-center.license-url="https://polyformproject.org/licenses/noncommercial/1.0.0" \
      dev.dhruv.job-search-command-center.required-notice="Required Notice: Copyright © 2026 Dhruv Jawalkar. Job Search Command Center was created and is maintained by Dhruv Jawalkar."
WORKDIR /app
# Ship only the compiled application and pnpm-generated production dependency
# tree. Do not ship the package manager, source, build cache, or development dependencies.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack /opt/yarn-v1.22.22 \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
      /usr/local/bin/pnpm /usr/local/bin/pnpx /usr/local/bin/yarn /usr/local/bin/yarnpkg
COPY --from=build --chown=node:node /app/dist ./dist
COPY --from=deploy --chown=node:node /runtime/node_modules ./node_modules
COPY --chown=node:node server.mjs ./server.mjs
ENV NODE_ENV=production
USER node
EXPOSE 3000
CMD ["node", "server.mjs"]
