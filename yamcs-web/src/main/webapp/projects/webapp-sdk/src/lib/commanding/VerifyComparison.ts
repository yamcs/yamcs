export interface VerifyComparison {
  parameter: string;
  operator: 'eq' | 'neq' | 'lt' | 'lte' | 'gt' | 'gte';
  value: any;
}
