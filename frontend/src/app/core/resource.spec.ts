import { describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { resource } from './resource';

describe('estados assíncronos', () => {
  it('descarta respostas antigas na troca de usuário', () => {
    const user = signal('primeiro');
    const first = new Subject<string>();
    const second = new Subject<string>();
    const result = TestBed.runInInjectionContext(() =>
      resource(() => (user() === 'primeiro' ? first : second)),
    );
    TestBed.tick();
    first.next('dados antigos');
    TestBed.tick();
    expect(result.data()).toBe('dados antigos');
    user.set('segundo');
    TestBed.tick();
    expect(result.loading()).toBe(true);
    expect(result.data()).toBeNull();
    first.next('resposta atrasada');
    second.next('dados atuais');
    TestBed.tick();
    expect(result.data()).toBe('dados atuais');
  });
  it('expõe falha de conexão sem substituir dados por sucesso fictício', () => {
    const stream = new Subject<string>();
    const result = TestBed.runInInjectionContext(() => resource(() => stream));
    TestBed.tick();
    stream.error(new HttpErrorResponse({ status: 0 }));
    TestBed.tick();
    expect(result.data()).toBeNull();
    expect(result.error()).toContain('conectar');
    expect(result.loading()).toBe(false);
  });
});
