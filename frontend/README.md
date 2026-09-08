# Command Center Portal

The local portal for the Job Search Command Center. It reads and writes through the loopback-only Spring Boot API at `http://127.0.0.1:8080` and displays representative, non-persistent preview data when that API is unavailable.

```powershell
pnpm install
pnpm run dev
```

Validation:

```powershell
pnpm run test
pnpm run lint
```

The development and production-preview commands bind to `127.0.0.1`; do not expose this unauthenticated personal workspace to a LAN or the public internet. Set `NEXT_PUBLIC_API_BASE_URL` only when the API uses another controlled loopback address.
