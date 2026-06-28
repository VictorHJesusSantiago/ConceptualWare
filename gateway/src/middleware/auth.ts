import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { JwtPayload, Role } from '../types/index.js';

/**
 * Concept #21 — Segurança: JWT, Bearer token, RBAC, Authorization
 * Concept #16 — HTTP: Authorization header, 401/403 status codes
 * Concept #8  — FP: Função pura de validação
 */

declare global {
  namespace Express {
    interface Request {
      user?: JwtPayload;
      requestId?: string;
    }
  }
}

const JWT_SECRET = process.env['JWT_SECRET'] ?? 'dev-secret-change-in-production';

// ── Pure token validation function ────────────────────────────────────────────

function validateToken(token: string): JwtPayload | null {
  try {
    return jwt.verify(token, JWT_SECRET) as JwtPayload;
  } catch {
    return null;
  }
}

function extractBearerToken(authHeader: string | undefined): string | null {
  if (!authHeader?.startsWith('Bearer ')) return null;
  return authHeader.slice(7);
}

// ── Authentication middleware ─────────────────────────────────────────────────

export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const token = extractBearerToken(req.headers['authorization']);

  if (!token) {
    res.status(401).json({ error: { code: 'MISSING_TOKEN', message: 'Authentication required' } });
    return;
  }

  const payload = validateToken(token);

  if (!payload) {
    res.status(401).json({ error: { code: 'INVALID_TOKEN', message: 'Invalid or expired token' } });
    return;
  }

  if (payload.type !== 'access') {
    res.status(401).json({ error: { code: 'WRONG_TOKEN_TYPE', message: 'Access token required' } });
    return;
  }

  req.user = payload;
  next();
}

// ── Optional authentication ───────────────────────────────────────────────────

export function optionalAuth(req: Request, _res: Response, next: NextFunction): void {
  const token = extractBearerToken(req.headers['authorization']);
  if (token) {
    const payload = validateToken(token);
    if (payload?.type === 'access') req.user = payload;
  }
  next();
}

// ── RBAC authorization middleware factory ─────────────────────────────────────

export function requireRole(...roles: Role[]) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      res.status(401).json({ error: { code: 'UNAUTHENTICATED', message: 'Authentication required' } });
      return;
    }
    const hasRole = roles.some(role => req.user!.roles.includes(role));
    if (!hasRole) {
      res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Insufficient permissions' } });
      return;
    }
    next();
  };
}

export const requireAdmin   = requireRole('ADMIN');
export const requirePremium = requireRole('PREMIUM', 'ADMIN');
