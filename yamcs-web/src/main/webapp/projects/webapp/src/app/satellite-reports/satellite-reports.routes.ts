import { inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  Routes,
} from '@angular/router';
import { YamcsService } from '@yamcs/webapp-sdk';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn } from '../core/guards/AuthGuard';
import { SatelliteReportsComponent } from './satellite-reports.component';

const attachReportContext: CanActivateFn = (route, state) =>
  route.queryParamMap.get('c')
    ? attachContextGuardFn(route, state)
    : inject(Router).createUrlTree(['/']);

const requireSatelliteReports: CanActivateFn = () => {
  const { instance } = inject(YamcsService).connectionInfo$.value ?? {};
  return (
    !!instance?.capabilities?.includes('satellite-reports') ||
    inject(Router).createUrlTree(['/404'])
  );
};

const contextChanged = (
  from: ActivatedRouteSnapshot,
  to: ActivatedRouteSnapshot,
) => from.queryParamMap.get('c') !== to.queryParamMap.get('c');

export const ROUTES: Routes = [
  {
    path: '',
    canActivate: [attachReportContext],
    runGuardsAndResolvers: contextChanged,
    children: [
      {
        // The capability depends on the instance attached by the parent guard.
        path: '',
        canActivate: [requireSatelliteReports],
        canActivateChild: [authGuardChildFn],
        runGuardsAndResolvers: contextChanged,
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: SatelliteReportsComponent,
            data: { view: 'latest' },
          },
          {
            path: 'history',
            component: SatelliteReportsComponent,
            data: { view: 'history' },
          },
          {
            path: 'reports/:id',
            component: SatelliteReportsComponent,
            data: { view: 'snapshot' },
          },
        ],
      },
    ],
  },
];
