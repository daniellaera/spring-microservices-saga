import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface ProfileResponse {
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  role: string;
}

export interface ProfileRequest {
  firstName: string;
  lastName: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private http = inject(HttpClient);

  profile = signal<ProfileResponse | null>(null);

  getProfile(): Observable<ProfileResponse> {
    return this.http.get<ProfileResponse>('/auth/profile').pipe(
      tap(p => this.profile.set(p))
    );
  }

  updateProfile(request: ProfileRequest): Observable<ProfileResponse> {
    return this.http.put<ProfileResponse>('/auth/profile', request).pipe(
      tap(p => this.profile.set(p))
    );
  }
}
