# ── 의존성 ──────────────────────────────────────────────────────────────────
# package.json 이 바뀌지 않으면 이 레이어는 캐시가 살아 있어 매번 다시 받지 않는다.
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ── 빌드 ────────────────────────────────────────────────────────────────────
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 텔레메트리를 끈다. 빌드가 외부로 나가지 않게 한다.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ── 실행 ────────────────────────────────────────────────────────────────────
FROM node:24-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV TZ=Asia/Seoul
# 컨테이너 밖(로드밸런서)에서 접근하려면 모든 인터페이스에 붙어야 한다.
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

RUN addgroup -S app && adduser -S app -G app

# standalone 출력에는 실행에 필요한 것만 들어 있다.
# static 과 public 은 따로 복사해야 한다 — standalone 이 담지 않는다.
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
COPY --from=build --chown=app:app /app/public ./public

USER app
EXPOSE 3000

# 페이지가 실제로 응답하는지 본다. 프로세스만 살아 있는 것으로는 부족하다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "require('http').get('http://127.0.0.1:3000/',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"

CMD ["node", "server.js"]
