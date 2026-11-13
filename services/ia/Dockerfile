# Service IA de Jamii. Construire depuis services/ia :  docker build -t jamii-ia services/ia
FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src src
RUN uv sync --frozen --no-dev && useradd --system --uid 10001 jamii
USER jamii
EXPOSE 8000
CMD ["/app/.venv/bin/python", "-m", "jamii_ia"]
