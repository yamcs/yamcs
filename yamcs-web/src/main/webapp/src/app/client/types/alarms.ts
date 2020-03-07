import { WebSocketCall } from '../WebSocketCall';

export interface GlobalAlarmStatus {
  unacknowledgedCount: number;
  unacknowledgedActive: boolean;
  acknowledgedCount: number;
  acknowledgedActive: boolean;
  shelvedCount: number;
  shelvedActive: boolean;
}

export interface SubscribeGlobalAlarmStatusRequest {
  instance: string;
  processor: string;
}

export type GlobalAlarmStatusSubscription = WebSocketCall<SubscribeGlobalAlarmStatusRequest, GlobalAlarmStatus>;
