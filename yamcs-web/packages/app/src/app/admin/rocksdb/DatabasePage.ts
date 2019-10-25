import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './DatabasePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasePage {

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

  compact() {
    this.snackBar.open(`Compacting ${this.tablespace}://${this.dbPath}...`, undefined, {
      horizontalPosition: 'end',
    });
    this.yamcs.yamcsClient.compactRocksDbDatabase(this.tablespace, this.dbPath).then(() => {
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
