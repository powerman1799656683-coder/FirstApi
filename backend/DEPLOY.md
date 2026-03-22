# Public Deployment

## Required environment variables

Set these before you deploy:

- `FIRSTAPI_ADMIN_PASSWORD`
- `FIRSTAPI_DATA_SECRET`
- `MYSQL_PASSWORD`

Optional but recommended:

- `FIRSTAPI_ADMIN_USERNAME`
- `FIRSTAPI_ADMIN_DISPLAY_NAME`
- `FIRSTAPI_ADMIN_EMAIL`
- `FIRSTAPI_USER_ENABLED=true`
- `FIRSTAPI_USER_USERNAME`
- `FIRSTAPI_USER_PASSWORD`
- `FIRSTAPI_SESSION_SECURE_COOKIE=true`
- `FIRSTAPI_SESSION_SAME_SITE=Strict`
- `MYSQL_USERNAME`
- `MYSQL_DATABASE`

## Windows deployment flow

1. Point a reverse proxy at the app and terminate HTTPS there.
2. Export the environment variables in the same shell session.
3. Run:

```powershell
cd D:\FirstApi\backend
.\deploy-prod.ps1 -Port 8080
```

The script will:

1. Build the Vite frontend.
2. Copy the frontend bundle into Spring Boot static assets.
3. Stop the process currently listening on the selected port.
4. Build the backend JAR.
5. Start the new JAR and write logs to `.runlogs`.

## Reverse proxy

Do not expose Spring Boot directly on the public internet.

Put `Nginx`, `Caddy`, or another reverse proxy in front of it and only expose `80/443`.
Set `FIRSTAPI_SESSION_SECURE_COOKIE=true` when HTTPS is enabled.

## Login bootstrap

On first startup the backend creates:

- one `ADMIN` account from the `FIRSTAPI_ADMIN_*` variables
- one `USER` account only if `FIRSTAPI_USER_ENABLED=true` and `FIRSTAPI_USER_PASSWORD` is set

After the first bootstrap, credentials live in MySQL and can be changed from the profile page.

## Relay smoke test

Before testing the public relay endpoint:

1. Insert or create a platform API key in `api_keys`.
2. Insert an upstream account in `accounts` with encrypted `credential`.
3. Leave `accounts.base_url` blank to use the official default provider URL, or set it to a compatible gateway base URL.

Non-stream test:

```powershell
curl.exe http://127.0.0.1:8080/v1/chat/completions `
  -H "Authorization: Bearer sk-firstapi-local" `
  -H "Content-Type: application/json" `
  -d "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with ok\"}]}"
```

Expected: `200 OK` and an OpenAI-style JSON body with `choices[0].message.content`.

Stream test:

```powershell
curl.exe -N http://127.0.0.1:8080/v1/chat/completions `
  -H "Authorization: Bearer sk-firstapi-local" `
  -H "Content-Type: application/json" `
  -d "{\"model\":\"gpt-4o-mini\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Count to three\"}]}"
```

Expected: multiple `data:` lines followed by `data: [DONE]`.
