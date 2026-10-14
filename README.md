# SmartStock

SmartStock is a full-stack stock portfolio management application for practicing portfolio tracking and simulated trading. It demonstrates Java/Spring service-layer business rules, React API integration, persistence, and role-protected administration.

> **Simulation only:** SmartStock does not connect to a brokerage and does not place real-money orders. All buys and sells are virtual transactions.

## Features

### Investor portal

- Register and sign in, browse supported stocks, and create virtual portfolios.
- Add and remove supported stocks from a per-user watchlist.
- Submit virtual buy and sell transactions. The backend uses its stored stock price, checks virtual cash and owned quantities, and records the resulting transactions.
- View holdings, average purchase costs, cash, portfolio valuation, transaction history, and realized/unrealized profit and loss.
- View historical daily prices and company fundamentals when the configured market-data provider supports the requested data.
- Keep a private investment journal with a thesis, lessons learned, optional tags, and an optional link to one of the account's completed virtual trades.

### Admin portal

- View aggregate platform counts for users, roles, stocks, portfolios, holdings, transactions, watchlist entries, and stock price availability.
- View users and update another user's role.
- Create, view, and update stock metadata. Market prices and their provider/source labels are not editable through the admin metadata form.
- Delete an unreferenced stock. Deletion is rejected if holdings, transactions, or watchlists reference it, preserving investor history.

Admin links in React are role-gated for usability. The backend independently protects `/api/v1/admin/**` with `ROLE_ADMIN`; hiding the link is not the security control.

## Technology and architecture

| Area | Technology |
| --- | --- |
| Frontend | React, JavaScript, Vite |
| Backend | Java 21, Spring Boot 3, Spring MVC, Spring Security |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL; in-memory H2 profile for local use |
| Schema | Flyway SQL migrations; Hibernate validates the schema |
| API | Authenticated REST endpoints using JSON |
| Market-data provider | Alpha Vantage for supported historical daily prices and fundamentals |

The frontend calls a dedicated API module; Spring controllers delegate to services, which enforce business rules and use repositories for persistence. See [docs/architecture.md](docs/architecture.md) for the component diagram and request flows.

## Requirements

- Java 21
- Maven 3.x
- Node.js compatible with Vite 8 (Node.js 20.19+ or 22.12+)
- PostgreSQL for persistent local data, or use the optional in-memory H2 profile

The repository does not include a Maven wrapper. Confirm `java`, `mvn`, `node`, and `npm` are available in your terminal.

## Local setup

### 1. Configure PostgreSQL

Create a local PostgreSQL login and database using credentials chosen for your machine. For example, run commands like these from a PostgreSQL administrator session, replacing the password placeholder locally:

```sql
CREATE USER smartstock WITH PASSWORD '<choose-a-local-password>';
CREATE DATABASE smartstock OWNER smartstock;
```

The backend applies `backend/src/main/resources/db/migration` migrations through Flyway. Hibernate is configured with `ddl-auto=validate`: it checks the mapped schema but does not create or drop the schema.

### 2. Set backend environment variables

The root and backend `.env.example` files are templates only. Spring Boot does **not** load `.env` files automatically. Set the variables in the same terminal used to start the backend, or configure them in your IDE run configuration.

PowerShell example (replace the password placeholder locally; do not commit it):

```powershell
$env:SMARTSTOCK_DB_URL = 'jdbc:postgresql://localhost:5432/smartstock'
$env:SMARTSTOCK_DB_USERNAME = 'smartstock'
$env:SMARTSTOCK_DB_PASSWORD = '<your-local-database-password>'
$env:FRONTEND_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'
```

`SMARTSTOCK_MARKET_DATA_API_KEY` is optional for starting the app. Set it only in the backend process environment if you want to request provider-backed history/fundamentals. Never place it in a `VITE_` frontend variable.

### 3. Start the backend

In a terminal with the backend environment set:

```powershell
Set-Location .\backend
mvn spring-boot:run
```

The API listens on port `8080` by default. The database must be available and the configured role must have access to the `smartstock` database.

### 4. Start the frontend

In another terminal:

```powershell
Set-Location .\frontend
npm install
npm run dev
```

Open the Vite URL, normally `http://localhost:5173`. The frontend API base defaults to `http://localhost:8080`; override it with `VITE_API_BASE_URL` in `frontend/.env` if needed.

### Optional in-memory H2 profile

For temporary local work without PostgreSQL, start the backend with the H2 profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'h2'
Set-Location .\backend
mvn spring-boot:run
```

H2 uses the project's migration in PostgreSQL compatibility mode. Its in-memory data is lost when the backend stops. The H2 console is disabled by default and should not be enabled or exposed in a production environment.

## Authentication and administration

- Public registration assigns only `USER`; the registration request cannot set a role.
- The application uses stateless HTTP Basic authentication for protected API requests. React keeps the entered username and password in memory for the current page session and sends them to the API; signing out or reloading clears that in-memory client state.
- Passwords are stored as BCrypt hashes. Login responses contain account metadata and persisted role names, not password hashes.
- The `User.roles` JPA element collection is persisted in the `user_roles` table.
- Admin APIs require `ROLE_ADMIN`. No public admin-registration endpoint or automatic admin bootstrap is provided. Provision an administrator only through a trusted, controlled local/deployment database-administration procedure after creating the account.
- Demo data seeding is disabled by default and enabled only by the local H2 profile. When enabled, the seeder creates a demo `USER` with a fixed credential and sample stocks if the corresponding tables are empty. Do not enable demo seeding or expose the demo account in a production deployment.
- HTTP Basic credentials must be protected by HTTPS outside localhost development. Configure production CORS origins explicitly and keep database/provider credentials in a secret manager or protected environment.

## API overview

Protected endpoints use HTTP Basic unless otherwise noted. Admin endpoints additionally require `ROLE_ADMIN`.

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Register an account with the `USER` role |
| `POST` | `/api/v1/auth/login` | Public | Validate credentials and return account metadata/roles |
| `GET` | `/api/v1/stocks` | Authenticated | Browse supported stocks and stored price-source details |
| `GET` | `/api/v1/stocks/{symbol}` | Authenticated | Retrieve a stock |
| `GET` | `/api/v1/stocks/{symbol}/history?range=1W\|1M\|3M` | Authenticated | Retrieve provider-backed daily history |
| `GET` | `/api/v1/stocks/{symbol}/fundamentals` | Authenticated | Retrieve fundamentals or a plan-restricted partial response |
| `GET` | `/api/v1/portfolios` | Authenticated, user-scoped | List the caller's portfolios |
| `POST` | `/api/v1/portfolios?name={name}` | Authenticated | Create a virtual portfolio |
| `PUT` | `/api/v1/portfolios/{portfolioId}` | Authenticated, owner-only | Rename the caller's portfolio using `{"name":"Retirement"}` |
| `POST` | `/api/v1/portfolios/{portfolioId}/buy` | Authenticated, owner-only | Execute a validated virtual buy |
| `POST` | `/api/v1/portfolios/{portfolioId}/sell` | Authenticated, owner-only | Execute a validated virtual sell |
| `GET` | `/api/v1/watchlist` | Authenticated, user-scoped | List the caller's watchlist |
| `POST` | `/api/v1/watchlist` | Authenticated, user-scoped | Add a stock using `{"symbol":"AAPL"}` |
| `DELETE` | `/api/v1/watchlist/{symbol}` | Authenticated, user-scoped | Remove a stock from the caller's watchlist |
| `GET` | `/api/v1/investment-journal` | Authenticated, user-scoped | List the caller's investment journal entries |
| `GET` | `/api/v1/investment-journal/trades` | Authenticated, user-scoped | List the caller's completed virtual trades for optional journal links |
| `POST` | `/api/v1/investment-journal` | Authenticated, user-scoped | Create a journal entry |
| `PUT` | `/api/v1/investment-journal/{entryId}` | Authenticated, owner-only | Edit one of the caller's journal entries |
| `DELETE` | `/api/v1/investment-journal/{entryId}` | Authenticated, owner-only | Delete one of the caller's journal entries |
| `GET` | `/api/v1/admin/dashboard` | Admin | Read aggregate platform statistics |
| `GET` | `/api/v1/admin/users` | Admin | List users without password hashes |
| `GET` | `/api/v1/admin/users/{userId}` | Admin | Read one sanitized user record |
| `PUT` | `/api/v1/admin/users/{userId}/role` | Admin | Change another user's role |
| `GET` | `/api/v1/admin/stocks` | Admin | List stock metadata |
| `GET` | `/api/v1/admin/stocks/{symbol}` | Admin | Read stock metadata |
| `POST` | `/api/v1/admin/stocks` | Admin | Add a stock (price initially unavailable) |
| `PUT` | `/api/v1/admin/stocks/{symbol}` | Admin | Update editable company/sector metadata |
| `DELETE` | `/api/v1/admin/stocks/{symbol}` | Admin | Delete only when no investor records reference the stock |

Backend security is authoritative. Unauthenticated requests are rejected; authenticated non-admin users are forbidden from admin routes. Portfolio and watchlist services derive ownership from the authenticated principal rather than a client-supplied user ID.

## Market-data limitations

- Seeded/sample stock prices are marked as `demo` and are simulated, not live market data.
- Newly created admin stocks have an unavailable price until a valid source is supplied; administrative metadata edits cannot fabricate or change prices.
- Historical prices use Alpha Vantage daily, unadjusted history. This is not a real-time quote feed. Provider availability, latency, rate limits, and plan restrictions apply.
- Company fundamentals use Alpha Vantage `OVERVIEW` and `INCOME_STATEMENT`. Some plan restrictions can produce a partial response with unavailable metrics. Missing configuration, provider errors, and quota restrictions are not guarantees of fresh data.
- In-memory provider caches are process-local and are cleared on restart.

## Verification

Run frontend checks from `frontend`:

```powershell
npm run lint
npm run build
```

Run all backend tests from `backend`:

```powershell
mvn test
```

The backend tests use the project test setup and embedded H2; they do not prove that a local PostgreSQL instance or external provider is configured correctly.

## Troubleshooting

- **Backend startup fails to connect to PostgreSQL:** verify PostgreSQL is running, the database and role exist, and `SMARTSTOCK_DB_URL`, `SMARTSTOCK_DB_USERNAME`, and `SMARTSTOCK_DB_PASSWORD` are set in the backend process environment.
- **Flyway or schema validation fails:** use a clean local database for a fresh setup. For an existing database, back it up and reconcile the applied Flyway migrations/schema; do not disable validation to conceal a mismatch.
- **Browser reports CORS/network errors:** verify the backend is reachable at `VITE_API_BASE_URL` and that the browser origin is included in `FRONTEND_ORIGINS`.
- **History/fundamentals are unavailable:** verify the backend provider key is configured. Alpha Vantage plan entitlements and request quotas may still limit data.
- **Admin navigation is absent or APIs return 403:** the current login account must already have a persisted `ADMIN` role. Public registration cannot grant it; there is no automatic admin account bootstrap.
- **Port is occupied:** stop only the SmartStock process you own or configure the relevant application port; do not terminate unrelated processes.

## Screenshots

Screenshots have not been added to this repository yet.

- `[Screenshot placeholder: investor dashboard]`
- `[Screenshot placeholder: admin dashboard]`
- `[Screenshot placeholder: stock discovery and watchlist]`

## Known limitations and future improvements

- The demo data seeder contains a fixed demo-user credential and currently has no production opt-out switch; an exposed deployment must not use the seeded demo account. Add an explicit, production-safe seed control before deploying publicly.
- There is no first-admin bootstrap workflow; role provisioning is an operator responsibility.
- Authentication uses HTTP Basic rather than a token or browser session mechanism. Use HTTPS and assess a dedicated production authentication/session design before exposing the service.
- Market-data provider limits and plan-dependent fields affect history and fundamentals; no real-time feed or brokerage integration is provided.
- Provider caches are in-memory and are not shared across application instances.
- Automated tests do not replace a deployment-specific PostgreSQL, CORS, secret-management, and authorization review.
