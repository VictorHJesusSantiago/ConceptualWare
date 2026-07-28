import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import compression from 'compression';
import pino from 'pino';
import pinoHttp from 'pino-http';
import { apiLimiter, authLimiter } from './middleware/rateLimiter.js';
import algorithmRoutes from './routes/algorithms.js';
import authRoutes from './routes/auth.js';
import userRoutes from './routes/users.js';
import coreConceptsRoutes from './routes/coreConcepts.js';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.js';
import { correlationId } from './middleware/correlationId.js';
import { allCircuitBreakersStatus, getCircuitBreaker } from './middleware/circuitBreaker.js';
import { cacheStats } from './middleware/responseCache.js';
import { listFeatureFlags } from './middleware/featureFlags.js';
import axios from 'axios';

/**
 * Concept #16 — HTTP/1.1, HTTP/2, Proxy Reverso, CORS, Headers
 * Concept #21 — Segurança: Helmet (CSP, HSTS, etc.), Rate limiting, CORS
 * Concept #18 — Async: Express é event-loop single-thread (Node.js Event Loop)
 * Concept #27 — Observabilidade: Pino structured logging com Trace ID
 * Concept #12 — BFF (Backend for Frontend) pattern
 */

const logger = pino({
  level: process.env['LOG_LEVEL'] ?? 'info',
  transport: process.env['NODE_ENV'] === 'development'
    ? { target: 'pino-pretty' }
    : undefined,
});

const app = express();

// ── Security headers — Defense in Depth (Concept #21) ────────────────────────

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:'],
    },
  },
  hsts: { maxAge: 31536000, includeSubDomains: true },
  referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
}));

// ── CORS (Concept #16 & #21) ──────────────────────────────────────────────────

app.use(cors({
  origin: (origin, callback) => {
    const allowed = [
      'http://localhost:3000',
      'http://localhost:5173',
      'http://localhost:80',
      process.env['FRONTEND_URL'],
    ].filter(Boolean);
    if (!origin || allowed.includes(origin)) callback(null, true);
    else callback(new Error('Not allowed by CORS'));
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Authorization', 'Content-Type', 'Accept', 'X-Request-ID'],
  exposedHeaders: ['X-Request-ID', 'X-RateLimit-Remaining', 'X-Trace-ID'],
}));

// ── Compression (Concept #26) ─────────────────────────────────────────────────

app.use(compression({ threshold: 2048 }));

// ── Correlation ID — Concept #27 (propagado ponta a ponta) ───────────────────

app.use(correlationId);

// ── Request parsing ────────────────────────────────────────────────────────────

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

// ── Structured logging — Concept #27 ─────────────────────────────────────────

app.use(pinoHttp({
  logger,
  customProps: (req) => ({
    requestId: req.headers['x-request-id'] ?? crypto.randomUUID().slice(0, 8),
  }),
  customLogLevel: (_req, res) =>
    res.statusCode >= 500 ? 'error' :
    res.statusCode >= 400 ? 'warn'  : 'info',
}));

// ── Health check — Liveness probe (Concept #27) ──────────────────────────────

app.get('/health', (_req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    version: process.env['npm_package_version'] ?? '1.0.0',
    uptime: process.uptime(),
  });
});

app.get('/health/ready', async (_req, res) => {
  // Readiness probe — checagem real do backend (não mais hardcoded)
  const backendUrl = process.env['BACKEND_URL'] ?? 'http://localhost:8080';
  let backendStatus: 'ok' | 'down' = 'down';
  try {
    await axios.get(`${backendUrl}/actuator/health`, { timeout: 2000 });
    backendStatus = 'ok';
  } catch {
    backendStatus = 'down';
  }

  const overallStatus = backendStatus === 'ok' ? 'ready' : 'not-ready';
  res.status(overallStatus === 'ready' ? 200 : 503).json({
    status: overallStatus,
    backend: backendStatus,
    circuitBreakers: allCircuitBreakersStatus(),
  });
});

// ── Diagnostics — cache stats, feature flags, circuit breakers ───────────────

app.get('/diagnostics', (_req, res) => {
  res.json({
    cache: cacheStats(),
    featureFlags: listFeatureFlags(),
    circuitBreakers: allCircuitBreakersStatus(),
  });
});

// Pré-registra o circuit breaker do backend para aparecer em /diagnostics desde o boot.
getCircuitBreaker('backend');

// ── Routes ────────────────────────────────────────────────────────────────────

app.use('/api/v1/auth',       authLimiter, authRoutes);
app.use('/api/v1/algorithms', apiLimiter,  algorithmRoutes);
app.use('/api/v1/users',      apiLimiter,  userRoutes);
app.use('/api/v1/core-concepts', apiLimiter, coreConceptsRoutes);

// ── Error handling ────────────────────────────────────────────────────────────

app.use(notFoundHandler);
app.use(errorHandler);

// ── Start ─────────────────────────────────────────────────────────────────────

const PORT = parseInt(process.env['PORT'] ?? '3001', 10);
const server = app.listen(PORT, () => {
  logger.info({ port: PORT, env: process.env['NODE_ENV'] }, 'ConceptualWare Gateway started');
});

// Graceful shutdown (Concept #17 — SIGTERM, SIGINT)
const shutdown = (signal: string) => {
  logger.info({ signal }, 'Shutdown signal received');
  server.close(() => {
    logger.info('HTTP server closed');
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000);
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT',  () => shutdown('SIGINT'));

export default app;
