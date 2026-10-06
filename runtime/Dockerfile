FROM --platform=$BUILDPLATFORM node:22-bookworm-slim AS backend
WORKDIR /build
COPY package.json package-lock.json tsconfig.json ./
RUN npm ci
COPY backend ./backend
COPY scripts/bundle-backend.mjs scripts/copy-runtime.mjs ./scripts/
RUN npm run build:android

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
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=backend /build/dist ./dist
COPY backend/runtime/terminal_bridge.py ./runtime/terminal_bridge.py
COPY web ./web
COPY runtime/android-compat.c /opt/pocket-agent/runtime/android-compat.c
RUN gcc -shared -fPIC -O2 /opt/pocket-agent/runtime/android-compat.c -o /usr/local/lib/libpocket-compat.so && \
    mkdir -p /workspace /state
COPY LICENSE /opt/pocket-agent/LICENSE
ENV HOME=/root POCKET_WORKSPACE=/workspace POCKET_STATE=/state
CMD ["node", "/opt/pocket-agent/dist/server.js"]
