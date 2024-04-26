import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild, input } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BitRange, Container, ExtractPacketResponse, ExtractedParameter, MessageService, Packet, Parameter, ParameterType, Value, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { HexComponent } from '../../../shared/hex/hex.component';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';

export interface INode {
  parent?: Node;
  expanded: boolean;
  rawValue?: Value;
  engValue?: Value;
}

export interface ContainerNode extends INode {
  type: 'CONTAINER';
  container: Container;
}

export interface SimpleParameterNode extends INode {
  type: 'SIMPLE_PARAMETER';
  location: number;
  size: number;
  parameter: Parameter;
}

export interface AggregateParameterNode extends INode {
  type: 'AGGREGATE_PARAMETER';
  location: number;
  size: number;
  parameter: Parameter;
}

export interface ArrayParameterNode extends INode {
  type: 'ARRAY_PARAMETER';
  location: number;
  size: number;
  parameter: Parameter;
}

export interface SimpleValueNode extends INode {
  type: 'SIMPLE_VALUE';
  parameter: Parameter;
  name: string;
  offset: string;
  parameterType: ParameterType;
  depth: number;
}

export interface ArrayValueNode extends INode {
  type: 'ARRAY_VALUE';
  parameter: Parameter;
  name: string;
  offset: string;
  parameterType: ParameterType;
  depth: number;
}

export interface AggregateValueNode extends INode {
  type: 'AGGREGATE_VALUE';
  parameter: Parameter;
  name: string;
  offset: string;
  parameterType: ParameterType;
  depth: number;
}

export type Node = ContainerNode
  | SimpleParameterNode
  | AggregateParameterNode
  | ArrayParameterNode
  | SimpleValueNode
  | AggregateValueNode
  | ArrayValueNode;

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
  ],
})
export class PacketComponent implements OnInit {

  packetName = input.required<string>({ alias: 'packet' });

  packet$ = new BehaviorSubject<Packet | null>(null);

  messages$ = new BehaviorSubject<string[]>([]);

  private allNodes: Node[] = [];
  dataSource = new MatTableDataSource<Node>();

  _hex?: HexComponent;

  displayedColumns = [
    'icon',
    'location',
    'size',
    'expand-aggray',
    'entry',
    'type',
    'rawValue',
    'engValue',
    'actions',
  ];

  containerColumns = [
    'icon',
    'containerName',
    'type',
    'rawValue',
    'engValue',
    'actions',
  ];

  typeOptions: YaSelectOption[] = [
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
    private title: Title,
    readonly route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private clipboard: Clipboard,
    private changeDetection: ChangeDetectorRef,
  ) { }

  ngOnInit(): void {
    const pname = this.packetName();
    const gentime = this.route.snapshot.paramMap.get('gentime')!;
    const seqno = Number(this.route.snapshot.paramMap.get('seqno')!);
    this.title.setTitle(pname);

    this.yamcs.yamcsClient.getPacket(this.yamcs.instance!, pname, gentime, seqno)
      .then(packet => this.packet$.next(packet))
      .catch(err => this.messageService.showError(err));

    this.yamcs.yamcsClient.extractPacket(this.yamcs.instance!, pname, gentime, seqno)
      .then(result => this.processResponse(result))
      .catch(err => this.messageService.showError(err));
  }

  private processResponse(result: ExtractPacketResponse) {
    this.messages$.next(result.messages || []);

    let prevContainerNode: ContainerNode | undefined;
    for (const pval of result.parameterValues || []) {
      if (pval.entryContainer.qualifiedName !== prevContainerNode?.container.qualifiedName) {
        const containerNode: ContainerNode = {
          type: 'CONTAINER',
          container: pval.entryContainer,
          expanded: false,
        };
        this.allNodes.push(containerNode);

        prevContainerNode = containerNode;
      }

      this.addParameterNodes(prevContainerNode, pval, this.allNodes);
    }

    // Expand last container
    if (prevContainerNode) {
      prevContainerNode.expanded = true;
    }

    this.updateDataSource();
  }

  private addParameterNodes(parent: Node, pval: ExtractedParameter, nodes: Node[]) {
    const { parameter, location, rawValue, engValue, size } = pval;
    if (parameter.type?.engType.endsWith('[]')) {
      const arrayNode: Node = {
        type: 'ARRAY_PARAMETER',
        parent,
        expanded: false,
        parameter,
        location,
        rawValue,
        engValue,
        size,
      };
      nodes.push(arrayNode);


      //  Nodes for array entries
      const rawValues = rawValue?.arrayValue || [];
      const engValues = engValue?.arrayValue || [];
      const entryType = parameter.type!.arrayInfo!.type;
      for (let i = 0; i < engValues.length; i++) {
        this.addValueNode(
          arrayNode,
          parameter,
          entryType,
          '[' + i + ']',
          '[' + i + ']',
          1,
          rawValues[i],
          engValues[i],
          nodes);
      }

    } else if (parameter.type?.engType === 'aggregate') {
      const aggregateNode: Node = {
        type: 'AGGREGATE_PARAMETER',
        parent,
        expanded: false,
        parameter,
        location,
        rawValue,
        engValue,
        size,
      };
      nodes.push(aggregateNode);

      // Nodes for aggregate members
      const rawAggregateValue = rawValue.aggregateValue!;
      const engAggregateValue = engValue.aggregateValue!;
      for (let i = 0; i < engAggregateValue.name.length; i++) {
        const memberType = parameter.type!.member[i].type as ParameterType;
        this.addValueNode(
          aggregateNode,
          parameter,
          memberType,
          engAggregateValue.name[i],
          '.' + engAggregateValue.name[i],
          1,
          rawAggregateValue.value[i],
          engAggregateValue.value[i],
          nodes);
      }
    } else {
      nodes.push({
        type: 'SIMPLE_PARAMETER',
        parent,
        expanded: false,
        parameter,
        location,
        rawValue,
        engValue,
        size,
      });
    }
  }

  private addValueNode(
    parent: Node,
    parameter: Parameter,
    parameterType: ParameterType,
    name: string,
    offset: string,
    depth: number,
    rawValue: Value,
    engValue: Value,
    nodes: Node[],
  ) {
    if (parameterType.engType.endsWith('[]')) {
      const node: ArrayValueNode = {
        type: 'ARRAY_VALUE',
        parent,
        expanded: false,
        parameter,
        parameterType,
        name,
        offset,
        rawValue,
        engValue,
        depth,
      };
      nodes.push(node);

      //  Nodes for array entries
      const rawValues = rawValue?.arrayValue || [];
      const engValues = engValue?.arrayValue || [];
      for (let i = 0; i < engValues.length; i++) {
        this.addValueNode(
          node,
          parameter,
          parameterType.arrayInfo!.type,
          '[' + i + ']',
          offset + '[' + i + ']',
          depth + 1,
          rawValues[i],
          engValues[i],
          nodes);
      }
    } else if (parameterType.engType === 'aggregate') {
      const node: AggregateValueNode = {
        type: 'AGGREGATE_VALUE',
        parent,
        expanded: false,
        parameter,
        parameterType,
        name,
        offset,
        rawValue,
        engValue,
        depth,
      };
      nodes.push(node);

      //  Nodes for aggregate members
      const rawAggregateValue = rawValue.aggregateValue!;
      const engAggregateValue = engValue.aggregateValue!;
      for (let i = 0; i < engAggregateValue.name.length; i++) {
        const memberType = parameterType.member[i].type as ParameterType;

        this.addValueNode(
          node,
          parameter,
          memberType,
          engAggregateValue.name[i],
          offset + '.' + engAggregateValue.name[i],
          depth + 1,
          rawAggregateValue.value[i],
          engAggregateValue.value[i],
          nodes);
      }
    } else {
      nodes.push({
        type: 'SIMPLE_VALUE',
        parent,
        expanded: false,
        parameter,
        parameterType,
        name,
        offset,
        rawValue,
        engValue,
        depth,
      });
    }
  }

  get hex() {
    return this._hex;
  }

  @ViewChild('hex')
  set hex(_hex: HexComponent | undefined) {
    this._hex = _hex;
  }

  highlightBitRange(node: Node) {
    if (node.type === 'SIMPLE_PARAMETER'
      || node.type === 'AGGREGATE_PARAMETER'
      || node.type === 'ARRAY_PARAMETER') {
      this.hex?.setHighlight(new BitRange(node.location, node.size));
    }
  }

  clearHighlightedBitRange() {
    this.hex?.setHighlight(null);
  }

  selectBitRange(node: Node) {
    if (node.type === 'SIMPLE_PARAMETER'
      || node.type === 'AGGREGATE_PARAMETER'
      || node.type === 'ARRAY_PARAMETER') {
      this.hex?.setSelection(new BitRange(node.location, node.size));
    }
  }

  toggleRow(node: Node) {
    if (this.isExpandable(node)) {
      if (node.expanded) {
        this.collapseNode(node);
      } else {
        node.expanded = true;
      }
    }
    this.updateDataSource();
  }

  collapseNode(node: Node) {
    for (const child of this.allNodes) {
      if (child.parent === node) {
        this.collapseNode(child);
      }
    }
    node.expanded = false;
    this.updateDataSource();
  }

  expandAll() {
    for (const node of this.allNodes) {
      if (this.isExpandable(node)) {
        node.expanded = true;
      }
    }
    this.updateDataSource();
  }

  collapseAll() {
    for (const node of this.allNodes) {
      node.expanded = false;
    }
    this.updateDataSource();
  }

  isExpandable(node: Node) {
    return node.type === 'CONTAINER'
      || node.type === 'AGGREGATE_PARAMETER'
      || node.type === 'ARRAY_PARAMETER'
      || node.type === 'AGGREGATE_VALUE'
      || node.type === 'ARRAY_VALUE';
  }

  /**
   * Renders only visible nodes
   */
  private updateDataSource() {
    const filteredNodes: Node[] = [];
    for (const node of this.allNodes) {
      if (!node.parent || node.parent?.expanded) {
        filteredNodes.push(node);
      }
    }
    this.dataSource.data = filteredNodes;
  }

  isContainer(index: number, node: Node) {
    return node.type === 'CONTAINER';
  }

  isNoContainer(index: number, node: Node) {
    return node.type !== 'CONTAINER';
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

  fillTypeWithValueDimension(engType: string, arrayValue?: Value[]) {
    // Note: in case of an array of arrays, we should set the last
    // [] occurrence only.
    if (engType.endsWith('[]')) {
      const length = arrayValue?.length || 0;
      engType = engType.substring(0, engType.length - 2) + '[' + length + ']';
    }

    // Any nested array can vary
    return engType.replaceAll('[]', '[?]');
  }
}
