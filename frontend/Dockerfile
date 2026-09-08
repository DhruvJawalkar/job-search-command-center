FROM node:22.22-alpine AS build
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
WORKDIR /app
ENV LOCALAPPDATA=/app/.pnpm-local
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN pnpm run build

FROM node:22.22-alpine
WORKDIR /app
COPY --from=build /app /app
ENV NODE_ENV=production
USER node
EXPOSE 3000
CMD ["node", "node_modules/vinext/dist/cli.js", "start", "--hostname", "0.0.0.0"]
