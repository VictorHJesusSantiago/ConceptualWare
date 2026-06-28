import { Router, Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import axios from 'axios';

/**
 * Concept #21 — Auth routes: register, login, refresh, logout
 * Concept #25 — REST: POST, 201/200/401/409 status codes
 * Concept #14 — Clean Code: Early return (guard clauses), input validation
 */

const router = Router();
const BACKEND = process.env['BACKEND_URL'] ?? 'http://localhost:8080';

// ── Validation schemas ────────────────────────────────────────────────────────

const RegisterSchema = z.object({
  email:    z.string().email().toLowerCase().trim(),
  username: z.string().min(3).max(50).regex(/^[a-zA-Z0-9_-]+$/),
  // Minimum 8 chars, at least 1 uppercase, 1 lowercase, 1 digit — Concept #21
  password: z.string().min(8).max(128)
    .regex(/[A-Z]/, 'Must contain uppercase')
    .regex(/[a-z]/, 'Must contain lowercase')
    .regex(/[0-9]/, 'Must contain digit'),
});

const LoginSchema = z.object({
  email:    z.string().email().toLowerCase().trim(),
  password: z.string().min(1),
});

const RefreshSchema = z.object({
  refreshToken: z.string().min(1),
});

// ── POST /api/v1/auth/register ────────────────────────────────────────────────

router.post('/register', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const body = RegisterSchema.parse(req.body);
    const response = await axios.post(`${BACKEND}/api/v1/auth/register`, body, { timeout: 10_000 });
    res.status(201).json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── POST /api/v1/auth/login ───────────────────────────────────────────────────

router.post('/login', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const body = LoginSchema.parse(req.body);
    const response = await axios.post(`${BACKEND}/api/v1/auth/login`, body, { timeout: 10_000 });
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── POST /api/v1/auth/refresh ─────────────────────────────────────────────────

router.post('/refresh', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { refreshToken } = RefreshSchema.parse(req.body);
    const response = await axios.post(
      `${BACKEND}/api/v1/auth/refresh`,
      { refreshToken },
      { timeout: 10_000 }
    );
    res.json(response.data);
  } catch (error) {
    next(error);
  }
});

// ── POST /api/v1/auth/logout ──────────────────────────────────────────────────

router.post('/logout', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { refreshToken } = RefreshSchema.parse(req.body);
    await axios.post(`${BACKEND}/api/v1/auth/logout`, { refreshToken }, { timeout: 5_000 });
    res.status(204).send();
  } catch (error) {
    next(error);
  }
});

export default router;
