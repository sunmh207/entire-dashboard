# Development Guide

This page contains the detailed developer documentation for Entire Dashboard.

## Project Architecture

Entire Dashboard follows a typical monorepo structure with separate frontend and backend applications:

```text
entire-dashboard/
├── server/                 # Spring Boot backend
│   ├── src/
│   │   └── main/
│   │       ├── java/       # Java source code (Spring Boot + JPA)
│   │       └── resources/
│   │           ├── application.yaml  # Backend configuration
│   │           └── db/migration/     # Flyway SQL migrations
│   ├── build.gradle.kts    # Gradle build file
│   └── gradlew             # Gradle wrapper
├── web/                    # Nuxt 4 frontend
│   ├── app/                # Vue 3 components & pages
│   ├── nuxt.config.ts      # Nuxt configuration
│   └── package.json        # Node dependencies
├── docker/                 # Docker configs (nginx, supervisord)
├── docker-compose.yml      # Docker Compose orchestration
└── Dockerfile              # Multi-stage build
```

## Tech Stack

**Backend**

- Java 25 + Spring Boot 4.1.0
- Spring Data JPA (Hibernate ORM)
- Spring Security + JWT authentication
- MySQL 8.0 database
- Flyway for database migrations
- Gradle for build management
- JGit for Git operations

**Frontend**

- Node.js 24 + pnpm 10
- Nuxt 4 (Vue 3 framework)
- Nuxt UI component library
- Pinia for state management
- ECharts for data visualization
- Tailwind CSS 4

## Communication Flow

1. **Frontend** runs on port `3001` in development, or port `80` in production via Nginx.
2. **Backend** runs on port `8080` with Spring Boot.
3. **Database** runs on port `3381` with MySQL.
4. Frontend proxies `/api/v1/**` requests to the backend via Nitro proxy rules.
5. Backend provides REST APIs and Swagger UI at `/swagger-ui.html`.

## Option 2: Local Development Environment

For active development, you can run the frontend and backend separately with hot reload enabled.

### Prerequisites

- **Java 25** (JDK 25+)
- **Node.js 24** (Node 24+)
- **pnpm 10** (`npm install -g pnpm`)
- **MySQL 8.0** (or Docker)

### 1. Setup Database

**Option A: Use Docker (Recommended)**

```bash
docker run -d \
  --name entire-dashboard-mysql \
  -e MYSQL_ROOT_PASSWORD=root123456 \
  -e MYSQL_DATABASE=entire-dashboard \
  -e TZ=Asia/Shanghai \
  -p 3381:3306 \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-authentication-plugin=mysql_native_password
```

Wait for MySQL to start:

```bash
docker logs entire-dashboard-mysql
```

**Option B: Use Local MySQL**

Create a database named `entire-dashboard` with UTF-8 encoding.

### 2. Start Backend

```bash
cd server

# Build dependencies (first time only)
./gradlew build -x test

# Run application
./gradlew bootRun
```

Backend will start on [http://localhost:8080](http://localhost:8080).

- **API Base URL**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

### 3. Start Frontend

Open a new terminal and run:

```bash
cd web

# Install dependencies (first time only)
pnpm install

# Run dev server
pnpm dev
```

Frontend will start on [http://localhost:3001](http://localhost:3001).

Nuxt proxies `/api/v1/**` requests to the backend at `http://127.0.0.1:8080`.

### 4. Configure Environment Variables (Optional)

Create `server/.env` to override defaults:

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=entire-dashboard
DB_USERNAME=root
DB_PASSWORD=root123456
JWT_SECRET=your-custom-secret-key
APP_USERNAME=admin
APP_PASSWORD=admin
```
