import { Component, computed, input, signal } from '@angular/core';
import { DiaGasto } from '../core/models';
import { ContextService } from '../core/context.service';
import { inject } from '@angular/core';
import { moneyEdit } from '../core/format';

@Component({
  selector: 'app-weekly-chart',
  template: `
    <div class="chart-area">
      <svg
        class="line-chart"
        viewBox="0 0 620 185"
        role="img"
        aria-label="Gastos por dia na semana"
      >
        <defs>
          <linearGradient id="week-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#a8f4c1" stop-opacity=".24" />
            <stop offset="1" stop-color="#a8f4c1" stop-opacity="0" />
          </linearGradient>
        </defs>
        @for (line of [26, 76, 126, 176]; track line) {
          <path [attr.d]="'M35 ' + line + ' H610'" stroke="#ffffff0b" stroke-dasharray="3 5" />
        }
        <path [attr.d]="area()" fill="url(#week-fill)" />
        <path
          class="line"
          [attr.d]="path()"
          fill="none"
          stroke="#a4efbc"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
          pathLength="1"
        />
        @for (point of points(); track point.data) {
          <circle
            class="chart-dot"
            [attr.cx]="point.x"
            [attr.cy]="point.y"
            r="3.5"
            fill="#b1f5c9"
            stroke="#17392b"
            stroke-width="2"
            tabindex="0"
            (focus)="active.set(point.data)"
            (blur)="active.set('')"
            (mouseenter)="active.set(point.data)"
            (mouseleave)="active.set('')"
          >
            <title>{{ point.label }}: {{ display(point.total) }}</title>
          </circle>
          @if (active() === point.data) {
            <text
              [attr.x]="Math.min(555, Math.max(65, point.x))"
              [attr.y]="Math.max(14, point.y - 15)"
              text-anchor="middle"
              style="fill:#d7f5e2;font-size:12px"
            >
              {{ display(point.total) }}
            </text>
          }
        }
      </svg>
      <div class="chart-labels">
        @for (point of points(); track point.data) {
          <span>{{ point.label }}</span>
        }
      </div>
    </div>
  `,
})
export class WeeklyChartComponent {
  readonly days = input.required<DiaGasto[]>();
  readonly active = signal('');
  readonly Math = Math;
  private readonly context = inject(ContextService);
  readonly points = computed(() => {
    const maximum = Math.max(...this.days().map((day) => day.total), 1);
    return this.days().map((day, index) => ({
      ...day,
      x: 40 + index * (560 / 6),
      y: 170 - (day.total / maximum) * 135,
      label: new Intl.DateTimeFormat('pt-BR', { weekday: 'short', timeZone: 'UTC' })
        .format(new Date(day.data + 'T12:00:00Z'))
        .replace('.', ''),
    }));
  });
  readonly path = computed(() =>
    this.points()
      .map((point, index) => (index === 0 ? 'M' : 'L') + point.x + ' ' + point.y)
      .join(' '),
  );
  readonly area = computed(() => (this.points().length ? this.path() + ' L600 180 L40 180 Z' : ''));
  display(value: number): string {
    return this.context.hidden() ? 'R$ •••••' : 'R$ ' + moneyEdit(value);
  }
}
