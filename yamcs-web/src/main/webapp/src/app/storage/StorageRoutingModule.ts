import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { ClearContextGuard } from '../core/guards/ClearContextGuard';
import { StoragePage } from './StoragePage';
import { BucketObjectsPage } from './buckets/BucketObjectsPage';
import { BucketPlaceholderPage } from './buckets/BucketPlaceHolderPage';
import { BucketPropertiesPage } from './buckets/BucketPropertiesPage';
import { BucketsPage } from './buckets/BucketsPage';


const routes: Routes = [{
  path: '',
  canActivate: [AuthGuard, ClearContextGuard],
  canActivateChild: [AuthGuard],
  runGuardsAndResolvers: 'always',
  component: StoragePage,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'buckets',
  }, {
    path: 'buckets',
    children: [{
      path: '',
      pathMatch: 'full',
      component: BucketsPage,
      data: { 'hasSidebar': false },
    }, {
      path: ':name',
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: 'objects',
      }, {
        path: 'objects',
        component: BucketPlaceholderPage,
        children: [{
          path: '**',
          component: BucketObjectsPage,
          data: { 'hasSidebar': false }
        }],
      }, {
        path: 'properties',
        component: BucketPropertiesPage,
        data: { 'hasSidebar': false },
      }]
    }]
  }]
}];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class StorageRoutingModule { }

export const routingComponents = [
  BucketsPage,
  BucketObjectsPage,
  BucketPlaceholderPage,
  BucketPropertiesPage,
];
