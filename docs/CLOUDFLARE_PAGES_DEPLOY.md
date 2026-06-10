# Cloudflare Pages Deployment

This project can be deployed to Cloudflare Pages as a static frontend site.

Cloudflare Pages Free can host the Vue/Vite frontend. It cannot run the Spring Boot backend jar. If backend APIs are required in production, deploy the backend separately and set `VITE_API_BASE` to that backend URL during the Pages build.

## GitHub Integration

Use these settings when creating the Pages project from the GitHub repository:

```text
Repository: mmu83626-maker/MindMap-AI-Sec
Production branch: main
Framework preset: Vite
Root directory: frontend
Build command: npm ci && npm run build
Build output directory: dist
```

Optional environment variable:

```text
VITE_API_BASE=https://your-backend.example.com
```

If `VITE_API_BASE` is not set, the frontend will try to call port `8090` on the visitor's current hostname. That is useful for local testing, but not for a public Pages site.

## Direct Upload With Wrangler

From the repository root:

```powershell
cd frontend
npm ci
npm run build
cd ..
npx wrangler pages deploy frontend/dist --project-name=mindmap-ai-sec
```

If Wrangler asks you to log in, complete Cloudflare authentication in the browser and rerun the deploy command.

## SPA Routing

`frontend/public/_redirects` is included so Cloudflare Pages serves `index.html` for client-side routes.
