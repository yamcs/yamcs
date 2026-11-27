import type { YamcsClient } from '@yamcs/webapp-sdk';
import type {
  ReportFilters,
  ReportHistoryPage,
  ReportSnapshot,
  ReportTypeItem,
} from './report-types';

export class ReportClient {
  private readonly client: Pick<YamcsClient, 'apiUrl' | 'doFetch'>;

  constructor(client: Pick<YamcsClient, 'apiUrl' | 'doFetch'>) {
    this.client = client;
  }

  async listTypes(instance: string) {
    const result = await this.get<{ types?: ReportTypeItem[] }>(instance, 'types');
    return result.types ?? [];
  }

  listReports(instance: string, filters: ReportFilters, next?: string) {
    return this.get<ReportHistoryPage>(instance, 'reports', {
      ...filters,
      next,
    });
  }

  getReport(instance: string, id: string) {
    return this.get<ReportSnapshot>(
      instance,
      'reports/' + encodeURIComponent(id),
    );
  }

  private async get<T>(
    instance: string,
    path: string,
    params?: Record<string, string | undefined>,
  ): Promise<T> {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value !== undefined) {
        query.set(key, value);
      }
    }
    const base = `${this.client.apiUrl}/satellite-reports/${encodeURIComponent(instance)}`;
    const response = await this.client.doFetch(
      `${base}/${path}${query.size ? '?' + query : ''}`,
    );
    return response.json();
  }
}
