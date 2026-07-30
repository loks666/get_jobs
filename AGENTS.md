# Repository Guidelines

## Project Structure

- `src/main/java/com/getjobs/` contains the Spring Boot backend and platform automation (`boss`, `liepin`, `job51`, and `zhilian`).
- `src/main/resources/` contains application configuration and runtime resources.
- `src/test/java/` contains backend tests.
- `front/app/`, `front/components/`, `front/lib/`, and `front/public/` contain the Next.js frontend.
- `db/getjobs.db` is local SQLite runtime data; do not treat database changes as source changes.

## Build, Test, and Development Commands

From the repository root:

```powershell
.\gradlew.bat test       # Run backend tests on JUnit Platform
.\gradlew.bat bootRun    # Start the Spring Boot service on port 8888
```

For the frontend:

```powershell
cd front
pnpm install              # Install frontend dependencies
pnpm dev                  # Start the development server
pnpm lint                 # Run ESLint
pnpm build                # Create a production build
```

## Coding Style & Naming

- Use four spaces in Java and two spaces in TypeScript/TSX.
- Use PascalCase for Java types and React components, camelCase for methods, fields, hooks, and utilities, and `UPPER_SNAKE_CASE` for constants.
- Keep browser automation serialized through the existing `PlaywrightManager`/`PlaywrightAccessGate`; avoid direct unsynchronized page access.
- No repository-wide formatter is configured; run `pnpm lint` and the Gradle test task before submitting changes.

## Testing Guidelines

Backend tests use JUnit 5 with Spring Boot test support and Mockito. Name test files `*Test.java` and describe behavior in test methods. Add focused regression coverage for automation selector or pagination changes, then run `.\gradlew.bat test`.

## Commits and Pull Requests

Use short, imperative English subjects; existing commits sometimes use an emoji prefix. Example: `fix(51job): support page-number navigation`. Pull requests should explain the behavior change and verification commands, link an issue when available, and include screenshots for frontend changes.

Do not commit `.codegraph/`, `.env`, cookies, generated `target/`, `front/.next/`, `front/node_modules/`, or local database files.

## Security and Runtime Data

Keep API keys, webhook URLs, cookies, and browser state in local ignored configuration. Automation interacts with real job platforms, so test with explicit account limits and avoid unintentional mass submissions.
