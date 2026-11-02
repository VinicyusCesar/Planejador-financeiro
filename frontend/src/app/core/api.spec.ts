import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService, userInterceptor } from './api.service';
import { ContextService } from './context.service';

describe('integração HTTP e propriedade', () => {
  let api: ApiService;
  let context: ContextService;
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([userInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(ApiService);
    context = TestBed.inject(ContextService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('permite selecionar um perfil antes de consultar finanças', () => {
    api.get('usuarios').subscribe();
    const request = http.expectOne('/api/usuarios');
    expect(request.request.headers.has('X-Usuario-Id')).toBe(false);
    request.flush([]);
  });
  it('envia o proprietário atual em recursos financeiros', () => {
    context.selectUser('usuario-a');
    api.get('contas').subscribe();
    const request = http.expectOne('/api/contas');
    expect(request.request.headers.get('X-Usuario-Id')).toBe('usuario-a');
    request.flush([]);
  });
  it('troca o cabeçalho quando muda o perfil', () => {
    context.selectUser('usuario-a');
    context.selectUser('usuario-b');
    api.get('cartoes').subscribe();
    const request = http.expectOne('/api/cartoes');
    expect(request.request.headers.get('X-Usuario-Id')).toBe('usuario-b');
    request.flush([]);
  });
  it('não consulta finanças sem perfil selecionado', () => {
    let status = 0;
    api.get('contas').subscribe({ error: (error) => (status = error.status) });
    expect(status).toBe(400);
    http.expectNone('/api/contas');
  });
  it('não envia identificação a outros servidores', () => {
    context.selectUser('usuario-a');
    TestBed.inject(HttpClient).get('https://example.com/teste').subscribe();
    const request = http.expectOne('https://example.com/teste');
    expect(request.request.headers.has('X-Usuario-Id')).toBe(false);
    request.flush({});
  });
  it('preserva paginação zero e remove filtros vazios', () => {
    context.selectUser('usuario-a');
    api.get('transacoes', { pagina: 0, tamanho: 20, tipo: '', cartaoId: null }).subscribe();
    const request = http.expectOne('/api/transacoes?pagina=0&tamanho=20');
    expect(request.request.params.has('cartaoId')).toBe(false);
    request.flush({});
  });
});
