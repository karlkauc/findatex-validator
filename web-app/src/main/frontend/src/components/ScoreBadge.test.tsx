import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ScoreBadge } from './ScoreBadge';

describe('ScoreBadge', () => {
  it('renders the percentage and the label', () => {
    render(<ScoreBadge label="Mandatory" percentage={67} />);
    expect(screen.getByText('Mandatory')).toBeInTheDocument();
    expect(screen.getByText('67')).toBeInTheDocument();
    expect(screen.getByText('/ 100')).toBeInTheDocument();
  });

  it.each([
    [95, /text-emerald-/, /bg-emerald-/],
    [90, /text-emerald-/, /bg-emerald-/],
    [89, /text-amber-/,   /bg-amber-/],
    [70, /text-amber-/,   /bg-amber-/],
    [69, /text-red-/,     /bg-red-/],
    [0,  /text-red-/,     /bg-red-/],
  ])('uses the right tone at %i%%', (pct, textRe, barRe) => {
    const { container } = render(<ScoreBadge label="x" percentage={pct} />);
    const numberSpan = screen.getByText(String(pct));
    expect(numberSpan.className).toMatch(textRe);
    // The progress bar is the inner div with explicit width — find it by inline width style.
    const bar = container.querySelector('div[style*="width:"]');
    expect(bar).not.toBeNull();
    expect(bar?.className).toMatch(barRe);
  });

  it('clamps the bar width to [0, 100]', () => {
    const { container: lo } = render(<ScoreBadge label="lo" percentage={-10} />);
    const { container: hi } = render(<ScoreBadge label="hi" percentage={150} />);
    const widthOf = (c: HTMLElement) => {
      const bar = c.querySelector('div[style*="width:"]') as HTMLElement | null;
      return bar?.style.width ?? '';
    };
    expect(widthOf(lo)).toBe('0%');
    expect(widthOf(hi)).toBe('100%');
  });

  it('applies the prominent style when prominent=true', () => {
    const { container } = render(<ScoreBadge label="Overall" percentage={80} prominent />);
    const card = container.firstElementChild as HTMLElement;
    expect(card.className).toContain('bg-navy-50');
  });

  it('uses the neutral style when prominent is omitted', () => {
    const { container } = render(<ScoreBadge label="Overall" percentage={80} />);
    const card = container.firstElementChild as HTMLElement;
    expect(card.className).toContain('bg-white');
    expect(card.className).not.toContain('bg-navy-50');
  });
});
