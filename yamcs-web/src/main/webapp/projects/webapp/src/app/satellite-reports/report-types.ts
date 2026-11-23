import type { Value } from '@yamcs/webapp-sdk';

export type ReportStatus = 'COMPLETE' | 'PARTIAL' | 'AMBIGUOUS' | 'NO_RESPONSE';

export interface ReportTypeItem {
  type: string;
  title: string;
  service: number;
  subtype: number;
  latest?: ReportSnapshot;
}

export interface ReportSnapshot {
  id: string;
  type: string;
  status: ReportStatus;
  firstReceived?: string;
  lastReceived?: string;
  assembledAt?: string;
  finalizedAt: string;
  sortTime: string;
  requestedAt?: string;
  responseDeadline?: string;
  command?: ReportCommand;
  expectedPages?: number;
  receivedPages?: number[];
  missingPages?: number[];
  rejectedPages?: number[];
  withheldPackets?: number;
  issues?: string[];
  pages?: ReportPage[];
}

export interface ReportCommand {
  id: string;
  name: string;
  embeddedIndex?: number;
  scope?: string;
}

export interface ReportPage {
  pageNumber: number;
  generationTime?: string;
  receptionTime?: string;
  apid?: number;
  sourceSequenceCount?: number;
  rows?: ReportRow[];
}

export interface ReportRow {
  cells?: ReportCell[];
  scheduledCommand?: ReportCommand;
}

export interface ReportCell {
  name: string;
  label: string;
  unit?: string;
  value?: Value;
}

export interface ReportFilters {
  type: string;
  start?: string;
  stop?: string;
}

export interface ReportHistoryPage {
  reports?: ReportSnapshot[];
  continuationToken?: string;
}
