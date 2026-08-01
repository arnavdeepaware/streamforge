.DEFAULT_GOAL := help

.PHONY: help backend-check web-check check clean

help: ## Show available validation targets.
	@awk 'BEGIN { FS = ":.*##"; printf "Usage: make <target>\n\nTargets:\n" } /^[a-zA-Z_-]+:.*##/ { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

backend-check: ## Run backend verification, including formatting checks.
	@./scripts/check-repository.sh backend

web-check: ## Run frontend formatting, lint, test, and build checks.
	@./scripts/check-repository.sh web

check: backend-check web-check ## Run every repository quality check.

clean: ## Remove generated backend and frontend build outputs.
	@./backend/mvnw -f backend/pom.xml clean
	@rm -rf web-dashboard/dist web-dashboard/node_modules/.tmp
