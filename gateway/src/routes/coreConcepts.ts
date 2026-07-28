import { Router, Request, Response, NextFunction } from 'express';
import axios from 'axios';
import { getCircuitBreaker, CircuitOpenError } from '../middleware/circuitBreaker.js';
import { forwardedHeaders } from '../middleware/correlationId.js';

/**
 * Concept #12 — BFF thin proxy: repassa ao backend as demos stateless de
 * conceitos "core" (grafos, DS avançadas, strings, concorrência, padrões
 * arquiteturais). Sem lógica de negócio aqui — só validação de shape via
 * passthrough e proteção via circuit breaker (mesma dependência downstream
 * que os outros proxies do gateway).
 */

const router = Router();
const BACKEND = process.env['BACKEND_URL'] ?? 'http://localhost:8080';
const backendCircuit = getCircuitBreaker('backend');

router.all('/*', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const targetPath = `/api/v1/core-concepts${req.path}`;
    const response = await backendCircuit.execute(() =>
      axios.request({
        method: req.method as any,
        url: `${BACKEND}${targetPath}`,
        params: req.query,
        data: req.body,
        timeout: 10_000,
        headers: forwardedHeaders(req),
        validateStatus: () => true, // repassa o status code do backend tal como está
      })
    );
    res.status(response.status).json(response.data);
  } catch (error) {
    if (error instanceof CircuitOpenError) {
      res.status(503).json({ error: 'Service temporarily unavailable', reason: error.message });
      return;
    }
    next(error);
  }
});

export default router;
