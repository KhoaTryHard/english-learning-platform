import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: { copyPublicDir: false },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.GATEWAY_URL || 'http://127.0.0.1:8080', changeOrigin: false },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}', 'tests/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    clearMocks: true,
  },
});
