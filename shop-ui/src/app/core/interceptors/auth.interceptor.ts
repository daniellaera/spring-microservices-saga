import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('token');
  const router = inject(Router);
  const authService = inject(AuthService);

  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: { Authorization: 'Bearer ' + token }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthEndpoint =
        req.url.includes('/auth/login') || req.url.includes('/auth/refresh');

      if (error.status === 401 && !isAuthEndpoint && !authService.isRefreshing()) {
        authService.isRefreshing.set(true);

        return authService.refresh().pipe(
          switchMap((response) => {
            authService.isRefreshing.set(false);
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${response.token}` }
            });
            return next(retryReq);
          }),
          catchError((refreshError) => {
            authService.isRefreshing.set(false);
            authService.logout();
            router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }

      if (error.status === 401 && !req.url.includes('/auth/login')) {
        authService.logout();
      }
      return throwError(() => error);
    })
  );
};
