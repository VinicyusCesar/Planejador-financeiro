import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpInterceptorFn, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { ContextService } from './context.service';
import { Query, ApiErro } from './models';

export const API_BASE = '/api';
export const userInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_BASE + '/')) return next(request);
  const context = inject(ContextService);
  const publicRoute = /^\/api\/(usuarios|health)(\/|$|\?)/.test(request.url);
  if (!publicRoute && !context.userId()) {
    return throwError(
      () =>
        new HttpErrorResponse({
          status: 400,
          error: { mensagem: 'Selecione um perfil para continuar.' },
        }),
    );
  }
  return next(
    context.userId()
      ? request.clone({ setHeaders: { 'X-Usuario-Id': context.userId()! } })
      : request,
  );
};
export function apiError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || error.status === 502 || error.status === 504)
      return 'Não conseguimos conectar ao servidor. Verifique se o backend está em execução e tente novamente.';
    const body = error.error as ApiErro | null;
    if (body && typeof body === 'object' && (body.mensagem || body.message))
      return body.mensagem || body.message || 'Não foi possível concluir.';
    if (error.status === 404) return 'Este registro não está disponível para o perfil selecionado.';
    if (error.status === 409)
      return 'Não foi possível concluir por uma regra financeira. Atualize os dados e tente novamente.';
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  get<T>(path: string, query: Query = {}): Observable<T> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query))
      if (value !== '' && value != null) params = params.set(key, String(value));
    return this.http.get<T>(API_BASE + '/' + path, { params });
  }
  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(API_BASE + '/' + path, body);
  }
  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(API_BASE + '/' + path, body);
  }
  patch(path: string): Observable<void> {
    return this.http.patch<void>(API_BASE + '/' + path, {});
  }
  delete(path: string): Observable<void> {
    return this.http.delete<void>(API_BASE + '/' + path);
  }
}
