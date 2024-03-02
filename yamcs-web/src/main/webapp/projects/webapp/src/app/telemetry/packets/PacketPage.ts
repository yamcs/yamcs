import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BitRange, ColumnInfo, Container, ExtractPacketResponse, ExtractedParameter, MessageService, Packet, SelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { Hex } from '../../shared/hex/Hex';

export interface ExtractedItem {
  type: 'PARAMETER' | 'CONTAINER';
  parent?: ExtractedItem;
  container?: Container;
  pval?: ExtractedParameter;
  expanded: boolean;
}

@Component({
  templateUrl: './PacketPage.html',
  styleUrls: ['./PacketPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacketPage implements AfterViewInit {

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    type: new UntypedFormControl('ANY'),
  });

  packet$ = new BehaviorSubject<Packet | null>(null);
  result$ = new BehaviorSubject<ExtractPacketResponse | null>(null);

  dataSource = new MatTableDataSource<ExtractedItem>();

  _hex?: Hex;

  columns: ColumnInfo[] = [
    { id: 'position', label: 'Position', visible: true },
    { id: 'entry', label: 'Entry', alwaysVisible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'rawValue', label: 'Raw value', visible: false },
    { id: 'engValue', label: 'Engineering value', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  typeOptions: SelectOption[] = [
    { id: 'ANY', label: 'Any type' },
    { id: 'aggregate', label: 'aggregate' },
    { id: 'array', label: 'array' },
    { id: 'binary', label: 'binary' },
    { id: 'boolean', label: 'boolean' },
    { id: 'enumeration', label: 'enumeration' },
    { id: 'float', label: 'float' },
    { id: 'integer', label: 'integer' },
    { id: 'string', label: 'string' },
    { id: 'time', label: 'time' },
  ];

  constructor(
    title: Title,
    readonly route: ActivatedRoute,
    readonly yamcs: YamcsService,
    messageService: MessageService,
  ) {
    const pname = decodeURIComponent(route.snapshot.paramMap.get('pname')!);
    const gentime = route.snapshot.paramMap.get('gentime')!;
    const seqno = Number(route.snapshot.paramMap.get('seqno')!);
    title.setTitle(pname);

    this.dataSource.filterPredicate = (node, filter) => {
      return !node.pval || (node.pval.parameter.qualifiedName.toLowerCase().indexOf(filter) >= 0);
    };

    yamcs.yamcsClient.getPacket(yamcs.instance!, pname, gentime, seqno).then(packet => {
      this.packet$.next(packet);
    }).catch(err => messageService.showError(err));

    yamcs.yamcsClient.extractPacket(yamcs.instance!, pname, gentime, seqno).then(result => {
      this.result$.next(result);
      const items: ExtractedItem[] = [];
      let prevContainerItem: ExtractedItem | undefined;
      for (const pval of result.parameterValues) {
        if (pval.entryContainer.qualifiedName !== prevContainerItem?.container?.qualifiedName) {
          const item: ExtractedItem = { type: 'CONTAINER', container: pval.entryContainer, expanded: false };
          items.push(item);
          prevContainerItem = item;
        }
        items.push({ type: 'PARAMETER', pval, parent: prevContainerItem, expanded: false });
      }

      // Expand last container
      if (prevContainerItem) {
        prevContainerItem.expanded = true;
      }

      this.dataSource.data = items;
    }).catch(err => messageService.showError(err));
  }

  ngAfterViewInit() {
    this.filterForm.valueChanges.subscribe(value => {
      this.dataSource.filter = (value.filter ?? '').toLowerCase();
    });
  }

  get hex() {
    return this._hex;
  }

  @ViewChild('hex')
  set hex(_hex: Hex | undefined) {
    this._hex = _hex;
  }

  highlightBitRange(item: ExtractedItem) {
    if (item.pval) {
      const { pval } = item;
      this.hex?.setHighlight(new BitRange(pval.location, pval.size));
    }
  }

  clearHighlightedBitRange() {
    this.hex?.setHighlight(null);
  }

  toggleRow(item: ExtractedItem) {
    if (item.container) {
      item.expanded = !item.expanded;
    }
  }
}
