import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Секое барање кон /api во развој се препраќа кон Spring Boot (порта 8080).
// Така нема CORS проблеми и frontend-от секогаш користи релативни патеки.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
