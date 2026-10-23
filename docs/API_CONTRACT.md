# API contract

Base path: `/api`

## Endpoints (planned)

- `GET /machines` — list registered machines.
- `GET /machines/{machineId}/inventory` — read current inventory.
- `POST /reservations` — reserve an available product.
- `GET /reservations/{reservationId}` — read reservation status.

Use JSON request and response bodies. Return standard HTTP status codes and
include an error message in JSON responses for failed requests.
