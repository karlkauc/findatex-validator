import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DesktopDownloadLink } from './DesktopDownloadLink';

describe('DesktopDownloadLink', () => {
  it('links to the configured download URL', () => {
    render(<DesktopDownloadLink url="https://example.test/findatex/releases" />);
    const link = screen.getByRole('link', { name: /offline app/i });
    expect(link.getAttribute('href')).toBe('https://example.test/findatex/releases');
    // Opens away from an in-progress validation, and rel guards the opener.
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('renders nothing when no URL is configured', () => {
    // An operator on their own instance may have no build to link to; the
    // header must then not show a dead action.
    const { container } = render(<DesktopDownloadLink url={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('treats a blank URL as unset', () => {
    const { container } = render(<DesktopDownloadLink url="   " />);
    expect(container.firstChild).toBeNull();
  });
});
