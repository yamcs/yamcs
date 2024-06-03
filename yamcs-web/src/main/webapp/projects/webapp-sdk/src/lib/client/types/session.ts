import { WebSocketCall } from '../WebSocketCall';

export interface SessionEvent {
  endReason: string;
}

export type SessionSubscription = WebSocketCall<{}, SessionEvent>;
