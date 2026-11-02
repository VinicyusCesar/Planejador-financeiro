import { computed, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, concat, map, Observable, of, switchMap } from 'rxjs';
import { apiError } from './api.service';

interface LoadState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}
export function resource<T>(request: () => Observable<T> | null) {
  const state = signal<LoadState<T>>({ data: null, loading: false, error: null });
  const revision = signal(0);
  toObservable(
    computed(() => {
      revision();
      return request();
    }),
  )
    .pipe(
      switchMap((stream) =>
        stream
          ? concat(
              of<LoadState<T>>({ data: null, loading: true, error: null }),
              stream.pipe(
                map((data) => ({ data, loading: false, error: null }) as LoadState<T>),
                catchError((error) =>
                  of<LoadState<T>>({ data: null, loading: false, error: apiError(error) }),
                ),
              ),
            )
          : of<LoadState<T>>({ data: null, loading: false, error: null }),
      ),
      takeUntilDestroyed(),
    )
    .subscribe((value) => state.set(value));
  return {
    data: computed(() => state().data),
    loading: computed(() => state().loading),
    error: computed(() => state().error),
    reload: () => revision.update((value) => value + 1),
  };
}
