import { ParameterValue, NamedObjectId } from '@yamcs/client';

export interface Display {

  title: string;
  width: number;
  height: number;

  parseAndDraw(id: string): Promise<any>;
  getBackgroundColor(): string;
  getParameterIds(): NamedObjectId[];
  getDataSourceState(): { green: boolean, yellow: boolean, red: boolean };

  digest(): void;
  processParameterValues(pvals: ParameterValue[]): void;

}
