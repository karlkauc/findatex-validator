import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SidebarLayout } from './SidebarLayout';

describe('SidebarLayout', () => {
  it('shows the sidebar content when expanded', () => {
    render(
      <SidebarLayout collapsed={false} onExpand={() => {}} sidebar={<p>sidebar</p>}>
        <p>main</p>
      </SidebarLayout>,
    );
    expect(screen.getByText('sidebar')).toBeInTheDocument();
    expect(screen.getByText('main')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Expand input panel' })).toBeNull();
    expect(document.getElementById('input-column')).not.toBeNull();
  });

  it('replaces the sidebar with a rail when collapsed', () => {
    render(
      <SidebarLayout collapsed onExpand={() => {}} sidebar={<p>sidebar</p>}>
        <p>main</p>
      </SidebarLayout>,
    );
    expect(screen.queryByText('sidebar')).toBeNull();
    const rail = screen.getByRole('button', { name: 'Expand input panel' });
    expect(rail).toHaveAttribute('aria-expanded', 'false');
    expect(rail).toHaveAttribute('aria-controls', 'input-column');
    expect(screen.getByText('main')).toBeInTheDocument();
  });

  it('calls onExpand when the rail is clicked', async () => {
    const user = userEvent.setup();
    const onExpand = vi.fn();
    render(
      <SidebarLayout collapsed onExpand={onExpand} sidebar={<p>sidebar</p>}>
        <p>main</p>
      </SidebarLayout>,
    );
    await user.click(screen.getByRole('button', { name: 'Expand input panel' }));
    expect(onExpand).toHaveBeenCalledTimes(1);
  });
});
