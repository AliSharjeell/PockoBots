FROM node:22-bookworm-slim AS runtime
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash git openssh-client python3 curl ca-certificates nano less procps \
    build-essential util-linux unzip iproute2 libicu72 && \
    rm -rf /var/lib/apt/lists/*
RUN tunnel_arch="$TARGETARCH"; if [ "$tunnel_arch" = amd64 ]; then tunnel_arch=x64; fi; \
    curl --fail --location --retry 3 "https://tunnelsassetsprod.blob.core.windows.net/cli/linux-${tunnel_arch}-devtunnel" \
    -o /usr/local/bin/devtunnel && chmod 755 /usr/local/bin/devtunnel
WORKDIR /opt/pocket-agent
COPY package.json package-lock.json tsconfig.json ./
RUN npm ci
COPY backend ./backend
COPY web ./web
RUN npx tsc && npm prune --omit=dev && \
    mkdir -p runtime /workspace /state && cp backend/runtime/terminal_bridge.py runtime/terminal_bridge.py && \
    rm -rf backend/test
COPY LICENSE /opt/pocket-agent/LICENSE
ENV HOME=/root POCKET_WORKSPACE=/workspace POCKET_STATE=/state
CMD ["node", "/opt/pocket-agent/dist/server.js"]
