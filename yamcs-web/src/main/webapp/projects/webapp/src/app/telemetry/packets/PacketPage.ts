import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BitRange, ExtractPacketResponse, ExtractedParameter, MessageService, Packet, SelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { Hex } from '../../shared/hex/Hex';

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

  dataSource = new MatTableDataSource<ExtractedParameter>();

  _hex?: Hex;

  displayedColumns = [
    'position',
    'parameter',
    'type',
    'rawValue',
    'engValue',
    'container',
    'actions',
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
    title.setTitle('Packet Content');
    const gentime = route.snapshot.paramMap.get('gentime')!;
    const seqno = Number(route.snapshot.paramMap.get('seqno')!);

    this.dataSource.filterPredicate = (pval, filter) => {
      return pval.parameter.qualifiedName.toLowerCase().indexOf(filter) >= 0;
    };

    yamcs.yamcsClient.getPacket(yamcs.instance!, gentime, seqno).then(packet => {
      this.packet$.next(packet);
    }).catch(err => messageService.showError(err));

    yamcs.yamcsClient.extractPacket(yamcs.instance!, gentime, seqno).then(result => {
      this.result$.next(result);
      this.dataSource.data = result.parameterValues;
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

  highlightBitRange(bitPosition: number, bitSize: number) {
    this.hex?.setHighlight(new BitRange(bitPosition, bitSize));
  }

  clearHighlightedBitRange() {
    this.hex?.setHighlight(null);
  }
}
