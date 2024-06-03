import { ArchiveRecord } from '@yamcs/webapp-sdk';

export interface ArchiveRecordGroup {
  name: string;
  records: ArchiveRecord[];
}
