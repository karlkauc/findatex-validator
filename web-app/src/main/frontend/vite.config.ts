/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Vite writes the production bundle directly into the Maven target dir so Quarkus
// picks it up as classpath:META-INF/resources/. Same-origin-only — no CORS needed.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: path.resolve(__dirname, '../../../target/classes/META-INF/resources'),
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
