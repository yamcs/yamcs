import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BitRange, ExtractPacketResponse, MessageService, Packet, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { HexComponent } from '../../../shared/hex/hex.component';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { PacketDetailTableComponent } from '../packet-detail-table/packet-detail-table.component';

@Component({
  standalone: true,
  templateUrl: './packet.component.html',
  styleUrl: './packet.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    HexComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    PacketDetailTableComponent,
  ],
})
export class PacketComponent implements OnInit {

  packet$ = new BehaviorSubject<Packet | null>(null);

  messages$ = new BehaviorSubject<string[]>([]);

  @ViewChild(HexComponent)
  private hex: HexComponent;

  @ViewChild(PacketDetailTableComponent)
  private packetDetailTable: PacketDetailTableComponent;

  constructor(
    private title: Title,
    readonly route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private clipboard: Clipboard,
    private changeDetection: ChangeDetectorRef,
  ) { }

  ngOnInit(): void {
    const pname = this.route.snapshot.paramMap.get('packet')!;
    const gentime = this.route.snapshot.paramMap.get('gentime')!;
    const seqno = Number(this.route.snapshot.paramMap.get('seqno')!);
    this.title.setTitle(pname);

    this.yamcs.yamcsClient.getPacket(this.yamcs.instance!, pname, gentime, seqno)
      .then(packet => this.packet$.next(packet))
      .catch(err => this.messageService.showError(err));

    this.yamcs.yamcsClient.extractPacket(this.yamcs.instance!, pname, gentime, seqno)
      .then(result => {
        this.packetDetailTable.processResponse(result);
      })
      .catch(err => this.messageService.showError(err));
  }

  expandAll() {
    this.packetDetailTable.expandAll();
  }

  collapseAll() {
    this.packetDetailTable.collapseAll();
  }

  copyHex(base64: string) {
    const hex = utils.convertBase64ToHex(base64);
    if (this.clipboard.copy(hex)) {
      this.messageService.showInfo('Hex copied');
    } else {
      this.messageService.showInfo('Hex copy failed');
    }
  }

  copyBinary(base64: string) {
    const raw = window.atob(base64);
    if (this.clipboard.copy(raw)) {
      this.messageService.showInfo('Binary copied');
    } else {
      this.messageService.showInfo('Binary copy failed');
    }
  }

  onHighlightBitRange(event: BitRange) {
    this.hex?.setHighlight(event);
  }

  onClearHighlightedBitRange() {
    this.hex?.setHighlight(null);
  }

  onSelectBitRange(event: BitRange) {
    this.hex?.setSelection(event);
  }
}
