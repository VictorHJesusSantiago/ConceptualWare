import { Router, Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import axios from 'axios';
import { authenticate, optionalAuth } from '../middleware/auth.js';
import { algorithmExecutionLimiter } from '../middleware/rateLimiter.js';
import { memoize, pipe } from '../utils/functional.js';
import { Category, Difficulty, AlgoSlug, ok, err } from '../types/index.js';

/**
 * Concept #25 — REST API: Endpoints, Paginação cursor/offset, Filtros
 * Concept #6  — Paradigma orientado a eventos, Async/await
 * Concept #18 — Async: async/await, Promise chaining
 * Concept #8  — FP: pipe, memoize, currying aplicados
 * Concept #21 — Sanitização de entradas, validação com Zod
 */

const router = Router();
const BACKEND = process.env['BACKEND_URL'] ?? 'http://localhost:8080';

// ── Input validation schemas (Concept #3 & #21) ───────────────────────────────

const ExecutionSchema = z.object({
  input: z.array(z.number().int().safe()).min(1).max(10_000),
});

const ListSchema = z.object({
  category: z.enum(['SORTING','SEARCHING','GRAPH','DYNAMIC_PROGRAMMING',
                     'STRING','GREEDY','DIVIDE_AND_CONQUER','BACKTRACKING',
                     'MATHEMATICAL','DATA_STRUCTURES','COMPRESSION','CRYPTOGRAPHY']).optional(),
  difficulty: z.enum(['EASY','MEDIUM','HARD','EXPERT']).optional(),
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(100).default(20),
  sort: z.string().default('name'),
  order: z.enum(['asc','desc']).default('asc'),
});

const SearchSchema = z.object({
  q: z.string().min(2).max(100).trim(),
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(50).default(10),
});

// ── Memoized backend calls (Concept #8 & #26) ─────────────────────────────────

const fetchAlgorithm = memoize(async (slug: string) => {
  const res = await axios.get(`${BACKEND}/api/v1/algorithms/${slug}`, { timeout: 5000 });
  return res.data;
});

// ── GET /api/v1/algorithms ────────────────────────────────────────────────────

router.get('/', optionalAuth, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const params = ListSchema.parse(req.query);
    const response = await axios.get(`${BACKEND}/api/v1/algorithms`, {
      params,
      timeout: 5000,
    });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── GET /api/v1/algorithms/search ─────────────────────────────────────────────

router.get('/search', optionalAuth, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const params = SearchSchema.parse(req.query);
    const response = await axios.get(`${BACKEND}/api/v1/algorithms/search`, {
      params,
      timeout: 5000,
    });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── GET /api/v1/algorithms/:slug ──────────────────────────────────────────────

router.get('/:slug', optionalAuth, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const slug = z.string().min(1).max(100).parse(req.params['slug']);
    const data = await fetchAlgorithm(slug as AlgoSlug);
    res.json(data);
  } catch (error) {
    next(error);
  }
});

// ── POST /api/v1/algorithms/:slug/execute ─────────────────────────────────────

router.post('/:slug/execute',
  algorithmExecutionLimiter,
  authenticate,
  async (req: Request, res: Response, next: NextFunction) => {
    try {
      const slug = z.string().min(1).max(100).parse(req.params['slug']);
      const body = ExecutionSchema.parse(req.body);

      // Pass user context to backend (Concept #21 — audit)
      const response = await axios.post(
        `${BACKEND}/api/v1/algorithms/${slug}/execute`,
        body,
        {
          headers: { 'X-User-ID': req.user?.sub ?? 'anonymous' },
          timeout: 10_000,
        }
      );
      res.json(response.data);
    } catch (error) {
      next(error);
    }
  }
);

// ── GET /api/v1/algorithms/:slug/complexity ───────────────────────────────────

router.get('/:slug/complexity', optionalAuth, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const slug = req.params['slug'] ?? '';
    const algo = await fetchAlgorithm(slug);

    // Transform response — Concept #8 (pure transformation)
    const complexityInfo = pipe<Record<string, unknown>>(
      (a: Record<string, unknown>) => ({ ...a, n50: computeOperations(algo.timeComplexity?.time, 50) }),
      (a: Record<string, unknown>) => ({ ...a, n100: computeOperations(algo.timeComplexity?.time, 100) }),
      (a: Record<string, unknown>) => ({ ...a, n1000: computeOperations(algo.timeComplexity?.time, 1000) }),
    )(algo);

    res.json(complexityInfo);
  } catch (error) {
    next(error);
  }
});

function computeOperations(notation: string, n: number): number {
  if (!notation) return n;
  if (notation.includes('n²')) return n * n;
  if (notation.includes('n log n')) return n * Math.log2(n);
  if (notation.includes('log n')) return Math.log2(n);
  if (notation === 'O(1)') return 1;
  return n; // O(n) default
}

export default router;
