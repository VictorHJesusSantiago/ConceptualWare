import { Router, Request, Response, NextFunction } from 'express';
import axios from 'axios';
import { authenticate } from '../middleware/auth.js';

const router = Router();
const BACKEND = process.env['BACKEND_URL'] ?? 'http://localhost:8080';

// ── GET /api/v1/users/me ──────────────────────────────────────────────────────

router.get('/me', authenticate, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const response = await axios.get(`${BACKEND}/api/v1/users/${req.user?.sub}`, {
      headers: { Authorization: req.headers['authorization'] ?? '' },
      timeout: 5_000,
    });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── GET /api/v1/users/leaderboard ────────────────────────────────────────────

router.get('/leaderboard', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const limit = Math.min(parseInt(String(req.query['limit'] ?? '10'), 10), 100);
    const response = await axios.get(`${BACKEND}/api/v1/users/leaderboard`, {
      params: { limit },
      timeout: 5_000,
    });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── GET /api/v1/users/me/progress ────────────────────────────────────────────

router.get('/me/progress', authenticate, async (req: Request, res: Response, next: NextFunction) => {
  try {
    const response = await axios.get(`${BACKEND}/api/v1/users/${req.user?.sub}/progress`, {
      headers: { Authorization: req.headers['authorization'] ?? '' },
      timeout: 5_000,
    });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

export default router;
