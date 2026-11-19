import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// port 5173 matches the backend's default CORS_ALLOWED_ORIGINS
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
