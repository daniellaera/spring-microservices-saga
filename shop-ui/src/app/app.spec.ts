import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfirmationService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { App } from './app';
import { environment } from '../environments/environment';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      // the providers of app.config.ts that the shell needs, without a backend
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        ConfirmationService
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the navbar with the build badge and the router outlet', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('app-navbar')).not.toBeNull();
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
    expect(compiled.querySelector('[title^="Build:"]')?.textContent?.trim())
      .toBe(`${environment.version} · ${environment.buildNumber}`);
  });
});
