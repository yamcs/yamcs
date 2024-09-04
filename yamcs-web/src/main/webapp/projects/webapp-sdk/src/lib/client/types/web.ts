import { WebSocketCall } from '../WebSocketCall';

export interface CreateQueryRequest {
  name: string;
  query: { [key: string]: any; };
  shared: boolean;
}

export interface Query {
  id: string;
  name: string;
  shared: boolean;
  query: { [key: string]: any; };
}

export interface ListQueriesResponse {
  queries?: Query[];
}

export interface EditQueryRequest {
  name: string;
  shared: boolean;
  query: { [key: string]: any; };
}

export interface ParseFilterRequest {
  resource: string;
  filter: string;
}

export interface ParseFilterData {
  errorMessage?: string;
  beginLine?: number;
  beginColumn?: number;
  endLine?: number;
  endColumn?: number;
}

export type ParseFilterSubscription = WebSocketCall<ParseFilterRequest, ParseFilterData>;
