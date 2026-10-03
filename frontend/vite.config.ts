import react from '@vitejs/plugin-react'
// defineConfig from 'vitest/config' = Vite's config plus a typed `test` section for Vitest.
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom', // a simulated browser DOM so React components can render in tests
    setupFiles: ['./src/test/setup.ts'],
  },
})
