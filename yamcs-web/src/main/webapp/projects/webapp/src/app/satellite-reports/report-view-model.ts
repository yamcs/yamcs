import type {
  ReportCell,
  ReportCommand,
  ReportSnapshot,
  ReportStatus,
  ReportTypeItem,
} from './report-types';

export const STATUS_LABELS: Record<ReportStatus, string> = {
  COMPLETE: 'Complete',
  PARTIAL: 'Partial',
  AMBIGUOUS: 'Ambiguous',
  NO_RESPONSE: 'No response',
};

export const REPORT_FIELD_HELP = {
  packetTimestamps:
    'Spacecraft generation time, Yamcs reception time, and CCSDS packet identifiers for each accepted page.',
  status:
    'Complete: all expected pages were assembled. Partial: some pages were missing or unusable. Ambiguous: some packets could not be assigned to one request. No response: no report packet was observed.',
  requestedAt:
    'When Yamcs started looking for the response, based on command send, verified execution, or the first matched echo for a scheduled command.',
  responseDeadline:
    'Report packets must have been received before this time to count toward the response.',
  firstReceived:
    'Yamcs reception time of the earliest packet observed for this attempt, including any withheld packet.',
  lastReceived:
    'Yamcs reception time of the latest packet observed for this attempt, including any withheld packet.',
  assembledAt:
    'When the last required page of a complete report reached Yamcs.',
  finalizedAt:
    'When Yamcs published the final snapshot after the response window and callback grace period.',
  receivedPages:
    'Usable pages assigned to this request, out of the total announced in telemetry when known.',
};

export const HISTORY_INTERVALS = [
  { id: 'PT1H', label: 'Last hour', hours: 1 },
  { id: 'PT6H', label: 'Last 6 hours', hours: 6 },
  { id: 'P1D', label: 'Last 24 hours', hours: 24 },
  { id: 'NO_LIMIT', label: 'No limit' },
  { id: 'CUSTOM', label: 'Custom' },
] as const;

export function getPresetHistoryRange(interval: string, now: Date) {
  if (interval === 'NO_LIMIT') {
    return { start: undefined, stop: undefined };
  }
  const preset = HISTORY_INTERVALS.find((option) => option.id === interval);
  if (preset && 'hours' in preset) {
    return {
      start: new Date(now.getTime() - preset.hours * 3_600_000).toISOString(),
      stop: now.toISOString(),
    };
  }
  return undefined;
}

export function selectLatest(item: ReportTypeItem) {
  return item.latest?.status === 'NO_RESPONSE' ? undefined : item.latest;
}

interface ReportTableRow {
  pageNumber: number;
  cells: Record<string, ReportCell | undefined>;
  scheduledCommand?: ReportCommand;
}

export function summarizeReport(report: ReportSnapshot) {
  const columns = new Map<string, string>();
  const rows: ReportTableRow[] = [];
  let hasScheduledCommands = false;

  for (const page of report.pages ?? []) {
    for (const row of page.rows ?? []) {
      const cells: ReportTableRow['cells'] = Object.create(null);
      for (const cell of row.cells ?? []) {
        if (!columns.has(cell.name)) {
          columns.set(cell.name, cell.label || cell.name);
        }
        cells[cell.name] = cell;
      }
      hasScheduledCommands ||= !!row.scheduledCommand?.id;
      rows.push({
        pageNumber: page.pageNumber,
        cells,
        scheduledCommand: row.scheduledCommand,
      });
    }
  }

  return {
    label: STATUS_LABELS[report.status],
    received: report.receivedPages?.length ?? 0,
    expected: report.expectedPages,
    missing: formatPageRanges(report.missingPages ?? []),
    rejected: formatPageRanges(report.rejectedPages ?? []),
    withheld: report.withheldPackets ?? 0,
    columns: Array.from(columns, ([name, label]) => ({ name, label })),
    rows,
    hasScheduledCommands,
  };
}

function formatPageRanges(pages: number[]) {
  const ranges: string[] = [];
  for (let index = 0; index < pages.length; index++) {
    const start = pages[index];
    let end = start;
    while (pages[index + 1] === end + 1) {
      end = pages[++index];
    }
    ranges.push(end === start ? String(start) : `${start}–${end}`);
  }
  return ranges.join(', ');
}
