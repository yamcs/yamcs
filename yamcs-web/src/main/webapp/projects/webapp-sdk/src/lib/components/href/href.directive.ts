import { LocationStrategy } from '@angular/common';
import { booleanAttribute, Directive, ElementRef, HostBinding, HostListener, Input, OnChanges, OnDestroy, Renderer2, ɵɵsanitizeUrlOrResourceUrl } from '@angular/core';
import { Event, NavigationEnd, Params, QueryParamsHandling, Router, UrlTree } from '@angular/router';
import { Subscription } from 'rxjs';
import { SdkBridge } from '../../services/sdk-bridge.service';

/**
 * Alternative implementation of the routerLink directive.
 *
 * RouterLink itself does not work well in a webcomponent.
 * This implementation does not use ActivatedRoute.
 */
@Directive({
  standalone: true,
  selector: '[yaHref]',
})
export class YaHref implements OnChanges, OnDestroy {

  @HostBinding('attr.target') @Input() target?: string;
  @Input() queryParams?: Params | null;
  @Input() queryParamsHandling?: QueryParamsHandling | null;
  @Input() fragment?: string;
  @Input() state?: { [k: string]: any; };
  @Input() info?: unknown;
  @Input({ transform: booleanAttribute }) preserveFragment: boolean = false;
  @Input({ transform: booleanAttribute }) skipLocationChange: boolean = false;
  @Input({ transform: booleanAttribute }) replaceUrl: boolean = false;

  private hrefInput: any[] | UrlTree | null = null;
  private href: string | null = null;
  private isAnchorElement: boolean;

  private subscription?: Subscription;

  constructor(
    private el: ElementRef,
    private readonly renderer: Renderer2,
    private localRouter: Router,
    private sdkBridge: SdkBridge,
    private locationStrategy?: LocationStrategy,
  ) {
    const tagName = el.nativeElement.tagName?.toLowerCase();
    this.isAnchorElement = tagName === 'a' || tagName === 'area';

    if (this.isAnchorElement) {
      this.subscription = localRouter.events.subscribe((s: Event) => {
        if (s instanceof NavigationEnd) {
          this.updateHref();
        }
      });
    }
  }

  ngOnChanges(): void {
    if (this.isAnchorElement) {
      this.updateHref();
    }
  }

  @Input()
  set yaHref(commandsOrUrlTree: string | any[] | UrlTree | null) {
    if (commandsOrUrlTree == null) {
      this.hrefInput = null;
    } else {
      if (commandsOrUrlTree instanceof UrlTree) {
        this.hrefInput = commandsOrUrlTree;
      } else {
        this.hrefInput = Array.isArray(commandsOrUrlTree)
          ? commandsOrUrlTree
          : [commandsOrUrlTree];
      }
    }
  }

  private updateHref(): void {
    const urlTree = this.urlTree;
    this.href =
      urlTree !== null && this.locationStrategy
        ? this.locationStrategy?.prepareExternalUrl(this.router.serializeUrl(urlTree))
        : null;

    const sanitizedValue =
      this.href === null
        ? null
        : ɵɵsanitizeUrlOrResourceUrl(
          this.href,
          this.el.nativeElement.tagName.toLowerCase(),
          'href',
        );
    this.applyAttributeValue('href', sanitizedValue);
  }

  private applyAttributeValue(attrName: string, attrValue: string | null) {
    const renderer = this.renderer;
    const nativeElement = this.el.nativeElement;
    if (attrValue !== null) {
      renderer.setAttribute(nativeElement, attrName, attrValue);
    } else {
      renderer.removeAttribute(nativeElement, attrName);
    }
  }

  get urlTree(): UrlTree | null {
    if (this.hrefInput === null) {
      return null;
    } else if (this.hrefInput instanceof UrlTree) {
      return this.hrefInput;
    }
    return this.router.createUrlTree(this.hrefInput, {
      queryParams: this.queryParams,
      fragment: this.fragment,
      queryParamsHandling: this.queryParamsHandling,
      preserveFragment: this.preserveFragment,
    });
  }

  @HostListener('click', [
    '$event.button',
    '$event.ctrlKey',
    '$event.shiftKey',
    '$event.altKey',
    '$event.metaKey',
  ])
  onClick(
    button: number,
    ctrlKey: boolean,
    shiftKey: boolean,
    altKey: boolean,
    metaKey: boolean,
  ): boolean {
    const urlTree = this.urlTree;

    if (urlTree === null) {
      return true;
    }

    if (this.isAnchorElement) {
      if (button !== 0 || ctrlKey || shiftKey || altKey || metaKey) {
        return true;
      }

      if (typeof this.target === 'string' && this.target != '_self') {
        return true;
      }
    }

    const extras = {
      skipLocationChange: this.skipLocationChange,
      replaceUrl: this.replaceUrl,
      state: this.state,
      info: this.info,
    };
    this.router.navigateByUrl(urlTree, extras);

    return !this.isAnchorElement;
  }

  get router() {
    // Fallback to local router if called before
    // SdkBridge is ready.
    //
    // It won't make valid URLs, but at least will
    // not emit errors.
    return this.sdkBridge.router || this.localRouter;
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
