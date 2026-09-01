import { PerFundScoreDto } from '../types/api';
import { ScoreBadge } from './ScoreBadge';

interface Props {
  perFundScores: PerFundScoreDto[];
}

// Mirrors core's ScorePercent: only a flawless score may read as 100, only a
// zero score as 0. Used just as a fallback — the API normally sends
// `percentage` already rounded this way.
function toPercent(value: number): number {
  const pct = Math.round(value * 100);
  if (pct >= 100 && value < 1) return 99;
  if (pct <= 0 && value > 0) return 1;
  return pct;
}

export function PerFundScores({ perFundScores }: Props) {
  if (perFundScores.length === 0) return null;
  return (
    <section aria-label="Per fund scores" className="space-y-3">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Per Fund</h3>
      <div className="space-y-3">
        {perFundScores.map((f) => (
          <div key={(f.portfolioId ?? '') + '|' + (f.valuationDate ?? '')} className="card">
            <div className="card-header">
              <span className="font-medium">{f.portfolioId ?? '(no portfolio id)'}</span>
              {f.portfolioName && <span className="ml-2 text-slate-500">— {f.portfolioName}</span>}
              {f.valuationDate && <span className="ml-2 text-xs text-slate-400">{f.valuationDate}</span>}
            </div>
            <div className="card-body">
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {f.scores.map((s) => (
                  <ScoreBadge
                    key={s.dimension}
                    label={prettyDim(s.dimension)}
                    percentage={s.percentage ?? toPercent(s.value ?? 0)}
                  />
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function prettyDim(d: string): string {
  return d.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}
