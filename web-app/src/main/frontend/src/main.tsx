import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { reportPageView } from './api/pageView';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 5 * 60 * 1000 },
  },
});

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('#root element not found');

// Fired outside the React tree: it must happen exactly once per page load,
// which an effect under StrictMode does not guarantee.
reportPageView();

createRoot(rootEl).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
