import {
  AfterContentInit,
  ChangeDetectionStrategy,
  Component,
  ContentChildren,
  effect,
  EffectRef,
  Injector,
  OnDestroy,
  QueryList,
  runInInjectionContext,
  signal,
} from '@angular/core';
import { YaStepperStep } from './stepper-step.component';

@Component({
  selector: 'ya-stepper',
  templateUrl: './stepper.component.html',
  styleUrls: ['./vars.css', './stepper.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-stepper',
  },
})
export class YaStepper implements AfterContentInit, OnDestroy {
  @ContentChildren(YaStepperStep)
  steps!: QueryList<YaStepperStep>;

  anyExpanded = signal(false);
  anyCollapsed = signal(false);

  // Track active effects to allow cleanup if needed
  private effectRefs: EffectRef[] = [];

  constructor(private injector: Injector) {}

  ngAfterContentInit(): void {
    this.setupExpandedListeners();
    this.steps.changes.subscribe(() => {
      this.setupExpandedListeners();
    });
  }

  collapseAll() {
    this.steps.forEach((step) => {
      step.expanded.set(false);
    });
  }

  expandAll() {
    this.steps.forEach((step) => {
      step.expanded.set(true);
    });
  }

  // Tricks to set anyExpanded and anyCollapsed
  private setupExpandedListeners() {
    this.effectRefs.forEach((ref) => ref.destroy());
    this.effectRefs = [];

    this.updateAnySignals();

    // Create an effect for each step's expanded signal
    this.steps.forEach(() => {
      const effectRef = runInInjectionContext(this.injector, () => {
        return effect(() => this.updateAnySignals());
      });
      this.effectRefs.push(effectRef);
    });
  }

  private updateAnySignals() {
    const anyExpanded = this.steps.toArray().some((step) => step.expanded());
    this.anyExpanded.set(anyExpanded);

    const anyCollapsed = this.steps.toArray().some((step) => !step.expanded());
    this.anyCollapsed.set(anyCollapsed);
  }

  ngOnDestroy(): void {
    this.effectRefs.forEach((ref) => ref.destroy());
    this.effectRefs = [];
  }
}
