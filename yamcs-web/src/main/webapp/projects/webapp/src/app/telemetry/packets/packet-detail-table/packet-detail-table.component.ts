import { ChangeDetectionStrategy, Component, Input, Output, EventEmitter } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BitRange, Container, ExtractPacketResponse, ExtractedParameter, Packet, Parameter, ParameterType, Value, WebappSdkModule, YaSelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

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
  selector: 'app-packet-detail-table',
  templateUrl: './packet-detail-table.component.html',
  styleUrls: ['./packet-detail-table.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [WebappSdkModule],
})
export class PacketDetailTableComponent {
  constructor(readonly yamcs: YamcsService) {}

  @Input() packet!: Packet;

  @Output() highlightBitRangeEvent = new EventEmitter<BitRange>();
  @Output() clearHighlightedBitRangeEvent = new EventEmitter<void>();
  @Output() selectBitRangeEvent = new EventEmitter<BitRange>();

  messages$ = new BehaviorSubject<string[]>([]);

  private allNodes: Node[] = [];
  dataSource = new MatTableDataSource<Node>();

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


  public processResponse(result: ExtractPacketResponse) {
    this.messages$.next(result.messages || []);

    this.allNodes = [];
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

  highlightBitRange(node: Node) {
    if (node.type === 'SIMPLE_PARAMETER'
      || node.type === 'AGGREGATE_PARAMETER'
      || node.type === 'ARRAY_PARAMETER') {
      this.highlightBitRangeEvent.emit(new BitRange(node.location, node.size));
    }
  }

  clearHighlightedBitRange() {
    this.clearHighlightedBitRangeEvent.emit();
  }

  selectBitRange(node: Node) {
    if (node.type === 'SIMPLE_PARAMETER'
      || node.type === 'AGGREGATE_PARAMETER'
      || node.type === 'ARRAY_PARAMETER') {
      this.selectBitRangeEvent.emit(new BitRange(node.location, node.size));
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

  private collapseNode(node: Node) {
    for (const child of this.allNodes) {
      if (child.parent === node) {
        this.collapseNode(child);
      }
    }
    node.expanded = false;
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
