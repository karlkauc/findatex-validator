import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExternalValidationToggle } from './ExternalValidationToggle';

function renderToggle(overrides: Partial<Parameters<typeof ExternalValidationToggle>[0]> = {}) {
  const props = {
    available: true,
    externalEnabled: false,
    leiEnabled: true,
    leiCheckLapsed: true,
    leiCheckName: false,
    leiCheckCountry: false,
    isinEnabled: true,
    isinCheckCurrency: false,
    isinCheckCic: false,
    apiKey: '',
    onExternalEnabledChange: vi.fn(),
    onLeiEnabledChange: vi.fn(),
    onLeiCheckLapsedChange: vi.fn(),
    onLeiCheckNameChange: vi.fn(),
    onLeiCheckCountryChange: vi.fn(),
    onIsinEnabledChange: vi.fn(),
    onIsinCheckCurrencyChange: vi.fn(),
    onIsinCheckCicChange: vi.fn(),
    onApiKeyChange: vi.fn(),
    ...overrides,
  } as Parameters<typeof ExternalValidationToggle>[0];
  return { props, ...render(<ExternalValidationToggle {...props} />) };
}

describe('ExternalValidationToggle', () => {
  it('renders nothing when available=false (operator master switch off)', () => {
    const { container } = renderToggle({ available: false });
    expect(container.firstChild).toBeNull();
  });

  it('shows only the master toggle when externalEnabled=false', () => {
    renderToggle({ externalEnabled: false });
    expect(screen.getByText(/GLEIF \/ OpenFIGI Online-Pr.fung aktivieren/)).toBeInTheDocument();
    // Sub-section labels should not be rendered yet.
    expect(screen.queryByText(/LEI gegen GLEIF pr.fen/)).not.toBeInTheDocument();
    expect(screen.queryByText(/ISIN gegen OpenFIGI pr.fen/)).not.toBeInTheDocument();
  });

  it('expands LEI + ISIN sections when externalEnabled=true', () => {
    renderToggle({ externalEnabled: true });
    expect(screen.getByText(/LEI gegen GLEIF pr.fen/)).toBeInTheDocument();
    expect(screen.getByText(/ISIN gegen OpenFIGI pr.fen/)).toBeInTheDocument();
    expect(screen.getByLabelText(/OpenFIGI API-Key/)).toBeInTheDocument();
  });

  it('disables LEI sub-checkboxes when leiEnabled=false', () => {
    renderToggle({ externalEnabled: true, leiEnabled: false });
    const lapsed = screen.getByLabelText(/Lapsed-Status/) as HTMLInputElement;
    const name = screen.getByLabelText(/Issuer-Name/) as HTMLInputElement;
    expect(lapsed.disabled).toBe(true);
    expect(name.disabled).toBe(true);
  });

  it('disables the API-key input when isinEnabled=false', () => {
    renderToggle({ externalEnabled: true, isinEnabled: false });
    const keyInput = screen.getByLabelText(/OpenFIGI API-Key/) as HTMLInputElement;
    expect(keyInput.disabled).toBe(true);
  });

  it('renders the API-key input as type=password and never echoes the key as plain text', () => {
    renderToggle({ externalEnabled: true, apiKey: 'secret-do-not-echo' });
    const keyInput = screen.getByLabelText(/OpenFIGI API-Key/) as HTMLInputElement;
    expect(keyInput.type).toBe('password');
    expect(keyInput.autocomplete).toBe('off');
    expect(keyInput.value).toBe('secret-do-not-echo');
    // The key should not appear anywhere as visible text.
    const visibleText = document.body.textContent ?? '';
    expect(visibleText).not.toContain('secret-do-not-echo');
  });

  it('forwards api key changes via onApiKeyChange', async () => {
    const user = userEvent.setup();
    const onApiKeyChange = vi.fn();
    renderToggle({ externalEnabled: true, onApiKeyChange });
    const keyInput = screen.getByLabelText(/OpenFIGI API-Key/);
    await user.type(keyInput, 'k');
    expect(onApiKeyChange).toHaveBeenCalledWith('k');
  });

  it('forwards master toggle changes via onExternalEnabledChange', async () => {
    const user = userEvent.setup();
    const onExternalEnabledChange = vi.fn();
    renderToggle({ externalEnabled: false, onExternalEnabledChange });
    const masterCheckbox = screen.getByLabelText(/GLEIF \/ OpenFIGI Online-Pr.fung aktivieren/);
    await user.click(masterCheckbox);
    expect(onExternalEnabledChange).toHaveBeenCalledWith(true);
  });
});
