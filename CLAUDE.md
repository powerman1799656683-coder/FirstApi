# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

### Backend (Spring Boot)
```bash
cd D:/FirstApi/backend
mvn package -DskipTests          # Build JAR
java -jar target/backend-0.0.1-SNAPSHOT.jar  # Run on port 8080
```

### Frontend (React + Vite)
```bash
cd D:/FirstApi/frontend
npm run dev      # Dev server on port 5173 (proxies /api to 8080)
npm run build    # Production build to dist/
npm run lint     # ESLint
```

### Deploy frontend to backend
```bash
rm -rf D:/FirstApi/backend/src/main/resources/static/*
cp -r D:/FirstApi/frontend/dist/* D:/FirstApi/backend/src/main/resources/static/
```
After deploying, the backend serves both API and frontend on port 8080.

## Architecture

Full-stack admin dashboard for an API relay/proxy platform ("YC-API HUB").

### Backend: Java 8 + Spring Boot 2.7.18
Three-layer architecture under `com.firstapi.backend`:

- **controller/** → Thin REST controllers, only HTTP mapping + delegation. All return `ApiResponse<T>`.
- **service/** → Business logic, validation, keyword filtering. Throws `ResponseStatusException(NOT_FOUND)` for missing entities.
- **repository/** → Data access via `SimpleStore<T>` (in-memory ConcurrentHashMap). Seed data loaded in `@PostConstruct`.
- **model/** → Entity classes implement `SimpleStore.Identifiable`. Nested `Request` inner class for POST/PUT payloads.
- **common/** → `SimpleStore<T>` (generic CRUD store), `ApiResponse<T>` (response wrapper), `PageResponse<T>` (list wrapper with items + total).
- **config/** → `WebConfig` (CORS for localhost:5173), `SpaWebConfig` (SPA fallback to index.html).

**API pattern**: `/api/admin/*` for admin endpoints, `/api/user/*` for user-facing endpoints.

**Important**: Uses `javax.annotation.PostConstruct` (NOT jakarta) because Spring Boot 2.7.

### Frontend: React 19 + Vite 8 + React Router DOM 7
- `src/api.js` → Fetch wrapper, all requests go through `/api` base path
- `src/App.jsx` → Router with 17 routes (12 admin + 5 user center)
- `src/components/Layout.jsx` → Main navigation shell
- `src/pages/` → 17 page components, each fetches data via `api.get/post/put/del`
- Charts use `recharts`, icons use `lucide-react`

### Data Flow
Frontend `api.js` → Vite proxy (dev) or direct (prod) → Spring controllers → services → repositories → `SimpleStore<T>` (ConcurrentHashMap)

All API responses follow: `{ success: boolean, message: string, data: T }`
List endpoints return: `{ success, message, data: { items: [...], total: N } }`

### No Database
All data is in-memory via `SimpleStore`. Data resets on restart. Repositories seed initial data in `@PostConstruct`.

### No Tests
No test suite exists. Manual testing via curl against running backend.

## 17 Modules

**CRUD entities** (9, each has controller → service → repository → model):
Users, Groups, Subscriptions, Accounts, Announcements, IPs, Promos, Redemptions, MyApiKeys

**Data-only modules** (8, service returns hardcoded/computed data, no repository):
Dashboard, Records, Monitor, Settings, Profile, MySubscription, MyRecords, MyRedemption
