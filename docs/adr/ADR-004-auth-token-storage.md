# ADR-004: Estratégia de Armazenamento de Tokens de Autenticação

**Status:** Aceito — Implementação em Fases

---

## Contexto

A aplicação é uma SPA (Single-Page Application) React que precisa manter tokens JWT (access token de 15 min + refresh token de 7 dias) entre navegações.

Existem três opções de armazenamento para tokens no browser:

| Opção | XSS | CSRF | Persiste recarregamento | Complexidade |
|---|---|---|---|---|
| `localStorage` | CRÍTICO — qualquer script lê | Não vulnerável | Sim | Baixa |
| `sessionStorage` | ALTO — scripts da mesma aba leem | Não vulnerável | Não (aba fechada) | Baixa |
| `HttpOnly Cookie` | Imune — JS não acessa | Requer SameSite=Strict ou token | Sim | Alta (backend + CORS) |
| Memória (variável JS) | Baixo — atacante precisa de XSS ativo | Não vulnerável | Não (recarregamento perde) | Média |

### Problema identificado

A implementação anterior armazenava `accessToken` e `refreshToken` diretamente em `localStorage` via o middleware `persist` do Zustand. Qualquer script injetado (XSS armazenado, extensão de browser maliciosa, supply-chain compromise) pode exfiltrar ambos os tokens com `localStorage.getItem('conceptualware-auth')`.

---

## Decisão

### Fase 1 — Implementada agora

- **Access token**: apenas em memória (estado Zustand, não persistido). Válido 15 min. Perdido no recarregamento da página.
- **Refresh token**: em `sessionStorage` (não `localStorage`). Persiste dentro da aba, mas não entre abas, janelas, ou ataques cross-tab. Será usadopara renovação silenciosa no carregamento da página.

**Resultado prático:** ao recarregar a página, o frontend tenta renovação silenciosa via `sessionStorage`. Se bem-sucedida, o usuário não percebe. Se falhar (token expirado, revogado), redireciona para login.

### Fase 2 — Target State (requer mudança no backend)

- **Nenhum token no JS**: backend emite `refreshToken` em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh`.
- **Access token**: mantido em memória, renovado silenciosamente via endpoint `/auth/silent-refresh` que lê o cookie automaticamente.
- **CSRF protection**: desnecessária para rotas com `SameSite=Strict` no cookie de refresh.

---

## Consequências

**Positivas:**
- Eliminação do vetor de exfiltração via XSS do refresh token de 7 dias
- Access token de 15 min em memória: janela de ataque mínima

**Negativas:**
- Recarregamento da página dispara uma requisição de refresh (latência de ~100ms antes da UI carregar dados autenticados)
- Cross-tab logout não automático (cada aba tem sua sessão independente em sessionStorage)

**Fase 2 adicional:**
- Requer mudança no `AuthController` (Spring) para emitir `Set-Cookie`
- Requer configuração CORS `withCredentials: true` (já está ativo no gateway e no backend)
