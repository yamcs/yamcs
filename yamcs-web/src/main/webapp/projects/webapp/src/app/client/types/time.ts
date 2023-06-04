import { WebSocketCall } from '../WebSocketCall';

export interface SubscribeTimeRequest {
    instance: string;
    processor?: string;
}

export interface Time {
    value: string;
}

export type TimeSubscription = WebSocketCall<SubscribeTimeRequest, Time>;
