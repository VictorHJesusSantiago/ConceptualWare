# ── Build Stage ────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

COPY tsconfig.json .
COPY src ./src
RUN npm run build

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM node:20-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY package.json .

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 3001

ENV NODE_ENV=production

CMD ["node", "dist/index.js"]
