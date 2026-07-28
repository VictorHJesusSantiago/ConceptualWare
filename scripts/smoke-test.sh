#!/usr/bin/env bash
# Smoke test pós-deploy local — verifica que os serviços essenciais respondem
# antes de considerar um deploy/subida local bem-sucedida. Não substitui a
# suíte de testes automatizados; é uma checagem rápida de "está tudo no ar?".
#
# Uso: bash scripts/smoke-test.sh
#   Variáveis opcionais: GATEWAY_URL, BACKEND_URL, FRONTEND_URL

set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:3001}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"

FAILURES=0

check() {
  local name="$1"
  local url="$2"
  local expected_status="${3:-200}"

  actual_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" || echo "000")

  if [ "$actual_status" = "$expected_status" ]; then
    echo "[OK]   $name — $url ($actual_status)"
  else
    echo "[FAIL] $name — $url (esperado $expected_status, obtido $actual_status)"
    FAILURES=$((FAILURES + 1))
  fi
}

echo "=== ConceptualWare — Smoke Test ==="
echo "Gateway:  $GATEWAY_URL"
echo "Backend:  $BACKEND_URL"
echo "Frontend: $FRONTEND_URL"
echo "-----------------------------------"

check "Backend health"        "$BACKEND_URL/actuator/health"
check "Backend algorithms"    "$BACKEND_URL/api/v1/algorithms"
check "Gateway health"        "$GATEWAY_URL/health"
check "Gateway readiness"     "$GATEWAY_URL/health/ready"
check "Gateway -> algorithms" "$GATEWAY_URL/api/v1/algorithms"
check "Frontend"              "$FRONTEND_URL"

echo "-----------------------------------"
if [ "$FAILURES" -eq 0 ]; then
  echo "Smoke test PASSOU — todos os serviços essenciais estão respondendo."
  exit 0
else
  echo "Smoke test FALHOU — $FAILURES verificação(ões) com problema."
  exit 1
fi
