import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { AggregateValue, Value } from '../../client';
import { ValuePipe } from '../../pipes/value.pipe';

const indent = 20;

interface ValueNode {
  margin: number;
  parent?: ValueNode;
  expanded: boolean;
  key?: string;
  value: Value;
  children?: ValueNode[];
}

@Component({
  standalone: true,
  selector: 'ya-value',
  templateUrl: './value.component.html',
  styleUrl: './value.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AsyncPipe,
    ValuePipe
],
})
export class YaValue implements OnChanges {

  @Input()
  value?: Value;

  @Input()
  alwaysExpand = false;

  nodes$ = new BehaviorSubject<ValueNode[]>([]);

  collapsed$ = new BehaviorSubject<boolean>(true);

  ngOnChanges() {
    const { value } = this;
    if (!value) {
      this.nodes$.next([]);
      return;
    }

    // Flattened list of nodes, which could be either
    // leafs or have (flattened) children.
    const nodes: ValueNode[] = [];
    this.processValue(value, this.alwaysExpand, nodes);
    this.nodes$.next(nodes);
  }

  private processValue(value: Value, expanded: boolean, appendTo: ValueNode[], parent?: ValueNode) {
    const node: ValueNode = {
      margin: parent ? parent.margin + indent : 0,
      parent,
      expanded,
      value,
    };
    appendTo.push(node);

    if (value.type === 'AGGREGATE') {
      node.children = this.processAggregateValue(value.aggregateValue!, appendTo, node);
    } else if (value.type === 'ARRAY') {
      node.children = this.processArrayValue(value.arrayValue || [], appendTo, node);
    }

    return node;
  }

  private processArrayValue(arrayValue: Value[], appendTo: ValueNode[], parent: ValueNode) {
    const directChildren: ValueNode[] = [];
    for (let i = 0; i < arrayValue.length; i++) {
      const value = arrayValue[i];
      const child = this.processValue(value, false, appendTo, parent);
      child.key = String(i);
      directChildren.push(child);
    }

    return directChildren;
  }

  private processAggregateValue(aggregateValue: AggregateValue, appendTo: ValueNode[], parent: ValueNode) {
    const directChildren: ValueNode[] = [];
    for (let i = 0; i < aggregateValue.name.length; i++) {
      const value = aggregateValue.value[i];
      const child = this.processValue(value, false, appendTo, parent);
      child.key = aggregateValue.name[i];
      directChildren.push(child);
    }

    return directChildren;
  }

  expandNode(node: ValueNode) {
    node.expanded = true;
    this.nodes$.next([...this.nodes$.value]);
  }

  collapseNode(node: ValueNode) {
    node.expanded = false;
    for (const child of node.children || []) {
      this.collapseNode(child);
    }
    this.nodes$.next([...this.nodes$.value]);
  }
}
