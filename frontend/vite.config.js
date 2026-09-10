import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Default Vite dev server port is 5173, which is what the backend's
// CORS config (app.cors.allowed-origin) allows by default.
export default defineConfig({
  plugins: [react()],
})
