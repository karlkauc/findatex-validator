// Mirrors the JSON DTOs returned by web-app/src/main/java/com/findatex/validator/web/dto/*.

export interface ProfileInfo {
  code: string;
  displayName: string;
}

export interface VersionInfo {
  version: string;
  label: string;
  releaseDate: string | null;
  profiles: ProfileInfo[];
}

export interface TemplateInfo {
  id: 'TPT' | 'EET' | 'EMT' | 'EPT' | string;
  displayName: string;
  versions: VersionInfo[];
  externalAvailable: boolean;
}

export type Severity = 'ERROR' | 'WARNING' | 'INFO';

export interface FindingDto {
  severity: Severity;
  ruleId: string;
  profileCode: string | null;
  profileDisplayName: string | null;
  fieldNum: string | null;
  fieldName: string | null;
  rowIndex: number | null;
  value: string | null;
  message: string;
  portfolioId: string | null;
  portfolioName: string | null;
  valuationDate: string | null;
  instrumentCode: string | null;
  instrumentName: string | null;
  valuationWeight: string | null;
}

export interface ScoreDto {
  dimension: string;
  value: number;
  percentage: number;
}

export interface ValidationSummary {
  templateId: string;
  templateVersion: string;
  filename: string;
  rowCount: number;
  findingCount: number;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  generatedAt: string;
}

export interface PerFundScoreDto {
  portfolioId: string | null;
  portfolioName: string | null;
  valuationDate: string | null;
  scores: ScoreDto[];
}

export interface ValidationResponse {
  summary: ValidationSummary;
  scores: ScoreDto[];
  perProfileScores: Record<string, ScoreDto[]>;
  perFundScores: PerFundScoreDto[];
  findings: FindingDto[];
  reportId: string;
}

export interface RateLimitStatus {
  limit: number;
  remaining: number;
  windowSeconds: number;
  resetInSeconds: number;
  desktopDownloadUrl?: string | null;
}

export class ApiError extends Error {
  constructor(public status: number, message: string, public retryAfterSeconds?: number) {
    super(message);
    this.name = 'ApiError';
  }
}
