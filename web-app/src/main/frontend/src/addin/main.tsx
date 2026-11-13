import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import TaskPane from './TaskPane';
import '../styles.css';

// No page-view beacon here: the pane is not a site visit, and the add-in's
// validation runs are already counted as web runs by the server.
function mount(content: ReactNode) {
  const rootEl = document.getElementById('root');
  if (!rootEl) throw new Error('#root element not found');
  createRoot(rootEl).render(<StrictMode>{content}</StrictMode>);
}

if (typeof Office === 'undefined') {
  mount(
    <p className="p-4 text-sm">
      This page is the FinDatEx Validator add-in for Excel. Load it through its manifest
      (/addin/manifest.xml) from inside Excel.
    </p>,
  );
} else {
  Office.onReady((info) => {
    if (info.host === Office.HostType.Excel) mount(<TaskPane />);
    else mount(<p className="p-4 text-sm">This add-in only runs in Excel.</p>);
  });
}
