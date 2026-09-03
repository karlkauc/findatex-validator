import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { TabPanel, TabStrip } from './Tabs';

function Harness() {
  const [active, setActive] = useState<'a' | 'b' | 'c'>('a');
  return (
    <>
      <TabStrip
        ariaLabel="Views"
        active={active}
        onChange={setActive}
        tabs={[
          { id: 'a', label: 'Alpha' },
          { id: 'b', label: 'Beta' },
          { id: 'c', label: 'Gamma' },
        ]}
      />
      <TabPanel id="a" active={active === 'a'}>panel a</TabPanel>
      <TabPanel id="b" active={active === 'b'}>panel b</TabPanel>
      <TabPanel id="c" active={active === 'c'}>panel c</TabPanel>
    </>
  );
}

describe('TabStrip / TabPanel', () => {
  it('renders a tablist with the selected tab and its visible panel', () => {
    render(<Harness />);
    expect(screen.getByRole('tablist', { name: 'Views' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Beta' })).toHaveAttribute('aria-selected', 'false');
    expect(screen.getByText('panel a')).toBeVisible();
    expect(screen.getByText('panel b')).not.toBeVisible();
  });

  it('switches panels on click and keeps hidden panels mounted', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('tab', { name: 'Beta' }));
    expect(screen.getByText('panel b')).toBeVisible();
    expect(screen.getByText('panel a')).not.toBeVisible();
    expect(screen.getByText('panel a')).toBeInTheDocument();
  });

  it('moves selection and focus with the arrow keys', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    screen.getByRole('tab', { name: 'Alpha' }).focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Beta' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Beta' })).toHaveFocus();
    await user.keyboard('{ArrowLeft}{ArrowLeft}');
    expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveAttribute('aria-selected', 'true');
  });

  it('jumps with Home and End', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    screen.getByRole('tab', { name: 'Alpha' }).focus();
    await user.keyboard('{End}');
    expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveAttribute('aria-selected', 'true');
    await user.keyboard('{Home}');
    expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('aria-selected', 'true');
  });
});
