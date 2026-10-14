# SmartStock frontend

React and Vite user interface for SmartStock. It calls the Spring Boot REST API; it does not persist trades or independently calculate portfolio values.

For full-stack setup, configuration, security notes, API routes, and verification commands, see the project [README](../README.md).

Copy `.env.example` to a local `.env` only when you need to override the API base URL. Vite exposes only `VITE_`-prefixed variables to browser code, so private API keys and database credentials must remain in the backend environment.
