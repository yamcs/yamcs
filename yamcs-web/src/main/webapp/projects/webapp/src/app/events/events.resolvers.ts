import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { ParseFilterSubscription, YamcsService } from '@yamcs/webapp-sdk';

/**
 * Resolver that waits for the ParseFilter subscription to be
 * fully established.
 *
 * This can be used to avoid timing issues for the initial
 * filter parse.
 */
export const resolveParseFilterSubscription: ResolveFn<ParseFilterSubscription> = (route, state) => {
  const yamcs = inject(YamcsService);
  const subscription = yamcs.yamcsClient.createParseFilterSubscription({
    resource: 'events',
    filter: '',
  }, () => null);

  return new Promise((resolve, reject) => {
    subscription.addReplyListener(() => {
      resolve(subscription);
    });
  });
};
