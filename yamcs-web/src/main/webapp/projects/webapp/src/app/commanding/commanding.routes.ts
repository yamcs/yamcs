import { Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { extensionMatcher } from '../appbase/extension/extension.matcher';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayControlCommandQueueGuardFn } from '../core/guards/MayControlCommandQueueGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { ClearancesActionLogTabComponent as ClearanceActionLogTab } from './clearances/clearances-action-log-tab/clearances-action-log-tab.component';
import { clearancesEnabledGuardFn } from './clearances/clearances-enabled.guard';
import { ClearancesListComponent } from './clearances/clearances-list/clearances-list.component';
import { CommandHistoryListComponent } from './command-history/command-history-list/command-history-list.component';
import { CommandComponent } from './command-history/command/command.component';
import { CommandReportComponent } from './command-sender/command-report/command-report.component';
import { ConfigureCommandComponent } from './command-sender/configure-command/configure-command.component';
import { SendCommandComponent } from './command-sender/send-command/send-command.component';
import { QueuedCommandsTabComponent } from './queues/queued-commands-tab/queued-commands-tab.component';
import { QueuesActionLogTabComponent } from './queues/queues-action-log-tab/queues-action-log-tab.component';
import { QueuesListComponent } from './queues/queues-list/queues-list.component';
import { StacksPageComponent } from './stacks/stacks-page/stacks-page.component';

const commandMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const command = '/' + consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'command': new UrlSegment(command, {}),
    },
  };
};

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: 'clearances',
    canActivate: [clearancesEnabledGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      component: ClearancesListComponent,
    }, {
      path: 'log',
      component: ClearanceActionLogTab,
    }]
  }, {
    path: 'send',
    children: [{
      path: '',
      pathMatch: 'full',
      component: SendCommandComponent,
    }, {
      matcher: commandMatcher,
      children: [{
        path: '',
        pathMatch: 'full',
        component: ConfigureCommandComponent,
      }, {
        path: '-/report/:commandId',
        component: CommandReportComponent,
      }],
    }]
  }, {
    path: 'history',
    children: [{
      path: '',
      pathMatch: 'full',
      component: CommandHistoryListComponent,
    }, {
      path: ':commandId',
      component: CommandComponent,
    }]
  }, {
    path: 'queues',
    component: QueuesListComponent,
    canActivate: [mayControlCommandQueueGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'pending',
    }, {
      path: 'pending',
      component: QueuedCommandsTabComponent,
    }, {
      path: 'log',
      component: QueuesActionLogTabComponent,
    }]
  }, {
    path: 'stacks',
    component: StacksPageComponent,
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    children: [{
      matcher: extensionMatcher,
      component: ExtensionComponent,
    }]
  }]
}];
