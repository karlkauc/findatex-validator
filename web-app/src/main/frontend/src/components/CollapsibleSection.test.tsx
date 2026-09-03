import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CollapsibleSection } from './CollapsibleSection';
import { UI_STORAGE_PREFIX } from '../lib/usePersistedState';

describe('CollapsibleSection', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('renders its children open by default', () => {
    render(
      <CollapsibleSection title="Scores" storageKey="scores" panelId="scores-grid">
        <p>content</p>
      </CollapsibleSection>,
    );
    const button = screen.getByRole('button', { name: 'Scores' });
    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(button).toHaveAttribute('aria-controls', 'scores-grid');
    expect(screen.getByText('content')).toBeInTheDocument();
    expect(document.getElementById('scores-grid')).not.toBeNull();
  });

  it('collapses on click, updates aria and persists the state', async () => {
    const user = userEvent.setup();
    render(
      <CollapsibleSection title="Per Fund" storageKey="perFund">
        <p>content</p>
      </CollapsibleSection>,
    );
    await user.click(screen.getByRole('button', { name: 'Per Fund' }));
    expect(screen.getByRole('button', { name: 'Per Fund' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('content')).toBeNull();
    expect(window.localStorage.getItem(UI_STORAGE_PREFIX + 'section.perFund')).toBe('0');
  });

  it('restores a persisted collapsed state on mount', () => {
    window.localStorage.setItem(UI_STORAGE_PREFIX + 'section.notes', '0');
    render(
      <CollapsibleSection title="Notes" storageKey="notes">
        <p>content</p>
      </CollapsibleSection>,
    );
    expect(screen.queryByText('content')).toBeNull();
  });

  it('respects defaultOpen=false when nothing is persisted', () => {
    render(
      <CollapsibleSection title="Notes" storageKey="notes" defaultOpen={false}>
        <p>content</p>
      </CollapsibleSection>,
    );
    expect(screen.queryByText('content')).toBeNull();
  });

  it('renders headerExtra next to the toggle', () => {
    render(
      <CollapsibleSection title="Notes" storageKey="notes" headerExtra={<span>extra</span>}>
        <p>content</p>
      </CollapsibleSection>,
    );
    expect(screen.getByText('extra')).toBeInTheDocument();
  });
});
