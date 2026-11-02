import { Component, input } from '@angular/core';

const PATHS: Record<string, string> = {
  home: 'M3 10.5 12 3l9 7.5V21h-6v-7H9v7H3z',
  activity: 'M3 7h18M3 12h12M3 17h8M17 15l3 3-3 3M20 18h-6',
  wallet: 'M4 6h14a2 2 0 0 1 2 2v12H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h13M16 11h6v5h-6z',
  card: 'M3 5h18v14H3zM3 9h18M7 15h4',
  chart: 'M12 3v9h9A9 9 0 1 1 12 3zM16 3.8A9 9 0 0 1 20.2 8H16z',
  target: 'M21 12a9 9 0 1 1-9-9M12 7a5 5 0 1 0 5 5M12 12l8-8M16 4h4v4',
  tag: 'M3 3h8l10 10-8 8L3 11zM7 7h.01',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M17 4a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87',
  transfer: 'M3 7h17l-4-4M21 17H4l4 4M20 7l-4 4M4 17l4-4',
  search: 'M20 20l-5-5M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13',
  plus: 'M12 5v14M5 12h14',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  up: 'M7 17 17 7M7 7h10v10',
  down: 'M7 7l10 10M7 17h10V7',
  left: 'M15 18l-6-6 6-6',
  right: 'M9 6l6 6-6 6',
  close: 'M6 6l12 12M18 6 6 18',
  check: 'M5 12l4 4L19 6',
  edit: 'm15 5 4 4M4 20l4-1L20 7a2.8 2.8 0 0 0-4-4L4 15z',
  trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7',
  calendar: 'M4 5h16v16H4zM8 3v4M16 3v4M4 10h16',
  filter: 'M4 7h16M7 12h10M10 17h4',
  eye: 'M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
  hidden:
    'm3 3 18 18M9.5 5.3A12 12 0 0 1 12 5c7 0 10 7 10 7a17 17 0 0 1-3 4M6 6a18 18 0 0 0-4 6s3 7 10 7a12 12 0 0 0 5-1M10 10a3 3 0 0 0 4 4',
  refresh: 'M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 12-1l2 3M18 17a7 7 0 0 1-12 1l-2-3',
  info: 'M12 8h.01M12 11v6M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  menu: 'M4 6h16M4 12h16M4 18h16',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z',
  power: 'M12 2v10M6 5a9 9 0 1 0 12 0',
};
@Component({
  selector: 'app-icon',
  template:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path()" /></svg>',
  styles: [
    ':host{display:inline-flex;width:1.25rem;height:1.25rem;flex:none;vertical-align:middle}svg{width:100%;height:100%}',
  ],
})
export class IconComponent {
  readonly name = input('spark');
  path(): string {
    return PATHS[this.name()] ?? PATHS['spark'];
  }
}
