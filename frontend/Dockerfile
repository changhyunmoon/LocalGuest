# 1단계: 빌드 (Build Stage)
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm install

# 모든 소스 복사 (이때 위에서 만든 .env.production도 포함됨)
COPY . .

# 여기서 빌드! (환경변수가 JS에 박히는 순간)
# Vite 기반이라면 dist 폴더가 생성됩니다.
RUN npm run build

# 2단계: 실행 (Production Stage)
FROM nginx:alpine
# 빌드된 정적 파일만 Nginx로 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx 기본 설정 덮어쓰기 (필요 시)
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]