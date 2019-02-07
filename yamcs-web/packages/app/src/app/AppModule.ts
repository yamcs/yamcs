import { APP_BASE_HREF } from '@angular/common';
import { Compiler, CompilerFactory, COMPILER_OPTIONS, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { JitCompilerFactory } from '@angular/platform-browser-dynamic';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppRoutingModule } from './AppRoutingModule';
import { AppUtilModule } from './apputil/AppUtilModule';
import { AppComponent } from './apputil/pages/AppComponent';
import { APP_CONFIG } from './core/config/AppConfig';
import { SharedModule } from './shared/SharedModule';

export function createCompiler(fn: CompilerFactory): Compiler {
  return fn.createCompiler();
}

@NgModule({
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    AppRoutingModule, // Keep in front of modules that contribute child routing
    AppUtilModule,
    SharedModule,
  ],
  providers: [
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    },
    {
      provide: APP_CONFIG,
      useValue: {},
    },
    {
      provide: COMPILER_OPTIONS,
      useValue: {},
      multi: true
    },
    {
      provide: CompilerFactory,
      useClass: JitCompilerFactory,
      deps: [COMPILER_OPTIONS]
    },
    {
      provide: Compiler, // The JIT compiler is used for dynamic modules
      useFactory: createCompiler,
      deps: [CompilerFactory]
    }
  ],
  exports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    SharedModule,
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule {
}
