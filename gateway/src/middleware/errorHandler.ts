import { Request, Response, NextFunction } from 'express';
import { ZodError } from 'zod';
import { v4 as uuid } from 'uuid';

/**
 * Concept #2  — Estruturas de Controle: try/catch/finally, throw, hierarquia de exceções
 * Concept #7  — OOP: Hierarquia de exceções, classe base de erro
 * Concept #16 — HTTP: Status codes 4xx/5xx
 * Concept #29 — Clean Code: Tratamento adequado de erros
 */

// Custom error hierarchy (Concept #7 — Herança, OOP)
export class AppError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly code: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

export class NotFoundError extends AppError {
  constructor(resource: string) {
    super(`${resource} not found`, 404, 'NOT_FOUND');
  }
}

export class ValidationError extends AppError {
  constructor(details: unknown) {
    super('Validation failed', 422, 'VALIDATION_ERROR', details);
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'Authentication required') {
    super(message, 401, 'UNAUTHORIZED');
  }
}

export class ForbiddenError extends AppError {
  constructor(message = 'Insufficient permissions') {
    super(message, 403, 'FORBIDDEN');
  }
}

// 404 handler
export function notFoundHandler(req: Request, res: Response): void {
  res.status(404).json({
    error: {
      code: 'NOT_FOUND',
      message: `Route ${req.method} ${req.path} not found`,
      requestId: uuid(),
    },
  });
}

// Global error handler (Concept #2 — Captura de múltiplas exceções)
export function errorHandler(
  error: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  const requestId = uuid();

  // Zod validation error
  if (error instanceof ZodError) {
    res.status(422).json({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Invalid request data',
        details: error.flatten(),
        requestId,
      },
    });
    return;
  }

  // Custom app errors
  if (error instanceof AppError) {
    res.status(error.statusCode).json({
      error: {
        code: error.code,
        message: error.message,
        details: error.details,
        requestId,
      },
    });
    return;
  }

  // Axios/upstream errors
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const axiosError = error as { response?: { status?: number; data?: unknown } };
    const status = axiosError.response?.status ?? 502;
    res.status(status).json({
      error: {
        code: 'UPSTREAM_ERROR',
        message: 'Backend service error',
        requestId,
      },
    });
    return;
  }

  // Unknown error — 500
  console.error('Unhandled error:', error);
  res.status(500).json({
    error: {
      code: 'INTERNAL_ERROR',
      message: 'An unexpected error occurred',
      requestId,
    },
  });
}
