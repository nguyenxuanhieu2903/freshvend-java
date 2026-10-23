# Local end-to-end test guide

1. Start PostgreSQL and create the development database using
   `scripts/setup_dev_db.sql`.
2. Configure local environment values from `.env.example`.
3. Start the backend with `mvn spring-boot:run` in `backend/`.
4. Start the frontend with `npm run dev` in `frontend/`.
5. Run `scripts/test_e2e_success.ps1` from PowerShell.

The script is a lightweight connectivity smoke test; extend it as API flows
are implemented.
