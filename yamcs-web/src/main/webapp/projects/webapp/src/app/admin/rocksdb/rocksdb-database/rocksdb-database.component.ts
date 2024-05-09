import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './rocksdb-database.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class RocksDbDatabaseComponent {

  tablespace: string;
  dbPath: string;
  properties$: Promise<string>;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    route: ActivatedRoute,
    private snackBar: MatSnackBar,
  ) {
    this.tablespace = route.snapshot.paramMap.get('tablespace')!;

    const routeSegments = route.snapshot.url;
    if (routeSegments.length) {
      this.dbPath = routeSegments.map(s => s.path).join('/');
    } else {
      this.dbPath = '';
    }

    title.setTitle(this.tablespace + '://' + this.dbPath);
    this.properties$ = yamcs.yamcsClient.getRocksDbDatabaseProperties(this.tablespace, this.dbPath);
  }

  compact(cfname: string) {
    this.snackBar.open(`Compacting ${this.tablespace}://${this.dbPath}...`, undefined, {
      horizontalPosition: 'end',
    });
    this.yamcs.yamcsClient.compactRocksDbDatabase(this.tablespace, this.dbPath, {
      cfname,
    }).then(() => {
      this.snackBar.open(`Compaction of ${this.tablespace}://${this.dbPath} successful`, undefined, {
        duration: 3000,
        horizontalPosition: 'end',
      });
    }).catch(err => {
      this.snackBar.open(`Compaction of ${this.tablespace}://${this.dbPath} failed`, undefined, {
        duration: 3000,
        horizontalPosition: 'end',
      });
    });
  }
}
