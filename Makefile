.PHONY: help up down build test lint typecheck smoke logs clean install

help:
	@echo "ConceptualWare — comandos disponíveis:"
	@echo "  make install    - instala dependências (gateway + frontend)"
	@echo "  make up         - sobe toda a stack local via docker compose"
	@echo "  make down       - derruba a stack local"
	@echo "  make build      - build de gateway e frontend"
	@echo "  make test       - roda testes de gateway e frontend"
	@echo "  make lint       - lint de gateway e frontend"
	@echo "  make typecheck  - typecheck de gateway e frontend"
	@echo "  make smoke      - smoke test pós-deploy local (requer stack no ar)"
	@echo "  make logs       - segue logs do docker compose"
	@echo "  make clean      - remove node_modules e artefatos de build"

install:
	cd gateway && npm install
	cd frontend && npm install

up:
	docker compose up -d --build

down:
	docker compose down

build:
	cd gateway && npm run build
	cd frontend && npm run build

test:
	cd gateway && npm test
	cd frontend && npm test -- --run

lint:
	cd gateway && npm run lint
	cd frontend && npm run lint

typecheck:
	cd gateway && npm run typecheck
	cd frontend && npm run typecheck

smoke:
	bash scripts/smoke-test.sh

logs:
	docker compose logs -f

clean:
	rm -rf gateway/node_modules gateway/dist
	rm -rf frontend/node_modules frontend/dist
