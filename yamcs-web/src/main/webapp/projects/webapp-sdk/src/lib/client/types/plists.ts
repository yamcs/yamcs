import { Parameter } from './mdb';

export interface CreateParameterListRequest {
  name: string;
  description?: string;
  patterns?: string[];
}

export interface ParameterList {
  id: string;
  name: string;
  description: string;
  patterns: string[];
  match?: Parameter[];
}

export interface GetParameterListsResponse {
  lists?: ParameterList[];
}

export interface UpdateParameterListRequest {
  name?: string;
  description?: string;
  patternDefinition?: PatternDefinition;
}

export interface PatternDefinition {
  patterns: string[];
}
