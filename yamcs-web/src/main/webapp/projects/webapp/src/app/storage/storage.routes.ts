import { Routes } from '@angular/router';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { clearContextGuardFn } from '../core/guards/ClearContextGuard';
import { BucketListComponent } from './buckets/bucket-list/bucket-list.component';
import { BucketObjectListComponent } from './buckets/bucket-object-list/bucket-object-list.component';
import { BucketPlaceholderComponent } from './buckets/bucket-placeholder/bucket-placeholder.component';
import { BucketPropertiesComponent } from './buckets/bucket-properties/bucket-properties.component';
import { StoragePageComponent } from './storage-page/storage-page.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, clearContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: StoragePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'buckets',
  }, {
    path: 'buckets',
    children: [{
      path: '',
      pathMatch: 'full',
      component: BucketListComponent,
      data: { 'hasSidebar': false },
    }, {
      path: ':name',
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: 'objects',
      }, {
        path: 'objects',
        component: BucketPlaceholderComponent,
        children: [{
          path: '**',
          component: BucketObjectListComponent,
          data: { 'hasSidebar': false }
        }],
      }, {
        path: 'properties',
        component: BucketPropertiesComponent,
        data: { 'hasSidebar': false },
      }]
    }]
  }]
}];
