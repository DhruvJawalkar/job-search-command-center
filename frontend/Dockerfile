ARG NODE_BUILD_IMAGE=node:22.22-alpine
ARG NODE_RUNTIME_IMAGE=node:22.22-alpine

FROM ${NODE_BUILD_IMAGE} AS build
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
WORKDIR /app
ENV LOCALAPPDATA=/app/.pnpm-local
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN pnpm run build

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
COPY --from=build /app /app
ENV NODE_ENV=production
USER node
EXPOSE 3000
CMD ["node", "node_modules/vinext/dist/cli.js", "start", "--hostname", "0.0.0.0"]
