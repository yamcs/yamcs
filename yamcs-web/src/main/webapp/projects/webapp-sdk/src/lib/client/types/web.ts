import { WebSocketCall } from '../WebSocketCall';

export interface CreateQueryRequest {
  name: string;
  query: { [key: string]: any };
  shared: boolean;
}

export interface Query {
  id: string;
  name: string;
  shared: boolean;
  query: { [key: string]: any };
}

export interface ListQueriesResponse {
  queries?: Query[];
}

export interface EditQueryRequest {
  name: string;
  shared: boolean;
  query: { [key: string]: any };
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

export type ParseFilterSubscription = WebSocketCall<
  ParseFilterRequest,
  ParseFilterData
>;

export interface ListNotificationsResponse {
  runtimeId: string;
  notifications?: Notification[];
}

export type NotificationType =
  | 'IN_PROGRESS'
  | 'INFO'
  | 'WARNING'
  | 'ERROR'
  | 'SUCCESS';

export interface Notification {
  tag: string;
  seq: number;
  instance: string;
  type: NotificationType;
  title: string;
  description?: string;
  timestamp: string;
  url?: string;
}

export interface SubscribeNotificationsRequest {}

export type NotificationSubscription = WebSocketCall<
  SubscribeNotificationsRequest,
  Notification
>;
