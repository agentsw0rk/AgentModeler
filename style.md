# Frontend Style Reference — Agent Modeler

Snapshot des aktuellen Frontend-Stylings (Stand: 2026-04-07), als Vorlage zum Übernehmen in andere Projekte.

## 1. Stack

| Layer | Tool / Version |
|-------|---------------|
| Framework | Next.js 16.2.1 (App Router) + React 19.2.4 |
| Styling-Engine | Tailwind CSS v4 (über `@tailwindcss/postcss`) |
| Animationen | `tw-animate-css` ^1.4 |
| UI-Primitives | `@base-ui/react` ^1.3 (NICHT Radix) |
| Component-Layer | shadcn 4.1 — Style: **`base-nova`**, Base Color: **neutral** |
| Variants | `class-variance-authority` (cva) + `tailwind-merge` + `clsx` |
| Icons | `lucide-react` |
| Zusatz-Styling | `styled-components` ^6 (vereinzelt für Rete-Nodes) |
| Code-Highlighting | `shiki` ^4 |
| Editor | `@monaco-editor/react` |

`postcss.config.mjs`:
```js
const config = { plugins: { "@tailwindcss/postcss": {} } };
export default config;
```

## 2. Tailwind v4 Setup

Tailwind v4 wird **rein CSS-basiert** konfiguriert — es gibt **keine `tailwind.config.js`**. Sämtliche Tokens, Theme-Variablen und Custom Variants leben in `app/globals.css`.

`app/globals.css` Import-Reihenfolge:
```css
@import "tailwindcss";
@import "tw-animate-css";
@import "shadcn/tailwind.css";

@custom-variant dark (&:is(.dark *));
```

`shadcn/tailwind.css` kommt aus dem shadcn-Paket und liefert die Basis-Token-Bridge.

## 3. Theme-Tokens (`@theme inline`)

Der Block mappt CSS-Custom-Properties auf Tailwind-Utilities (z. B. `bg-primary`, `text-muted-foreground`, `rounded-md`).

```css
@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --font-sans: var(--font-sans);
  --font-mono: var(--font-geist-mono);
  --font-heading: var(--font-sans);

  /* Sidebar-Slot */
  --color-sidebar: var(--sidebar);
  --color-sidebar-foreground: var(--sidebar-foreground);
  --color-sidebar-primary: var(--sidebar-primary);
  --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
  --color-sidebar-accent: var(--sidebar-accent);
  --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
  --color-sidebar-border: var(--sidebar-border);
  --color-sidebar-ring: var(--sidebar-ring);

  /* Charts */
  --color-chart-1: var(--chart-1);
  --color-chart-2: var(--chart-2);
  --color-chart-3: var(--chart-3);
  --color-chart-4: var(--chart-4);
  --color-chart-5: var(--chart-5);

  /* Standard-Slots */
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);

  /* Radius-Skala (auf Basis von --radius) */
  --radius-sm: calc(var(--radius) * 0.6);
  --radius-md: calc(var(--radius) * 0.8);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) * 1.4);
  --radius-2xl: calc(var(--radius) * 1.8);
  --radius-3xl: calc(var(--radius) * 2.2);
  --radius-4xl: calc(var(--radius) * 2.6);
}
```

## 4. Farbpalette (Dark-only, Blue-Accent)

Das Projekt läuft **ausschließlich im Dark-Mode** (`<html className="dark">`). Es existiert nur ein `:root`-Block — kein heller Modus. Farben sind in **OKLCH** definiert.

```css
:root {
  --background:        oklch(0.13 0.02 260);
  --foreground:        oklch(0.93 0.01 250);

  --card:              oklch(0.17 0.02 260);
  --card-foreground:   oklch(0.93 0.01 250);
  --popover:           oklch(0.17 0.02 260);
  --popover-foreground:oklch(0.93 0.01 250);

  --primary:           oklch(0.62 0.19 260);   /* Blau-Akzent */
  --primary-foreground:oklch(0.98 0 0);

  --secondary:         oklch(0.22 0.03 260);
  --secondary-foreground: oklch(0.93 0.01 250);

  --muted:             oklch(0.22 0.03 260);
  --muted-foreground:  oklch(0.55 0.03 250);

  --accent:            oklch(0.22 0.03 260);
  --accent-foreground: oklch(0.93 0.01 250);

  --destructive:       oklch(0.65 0.20 25);

  --border:            oklch(1 0 0 / 10%);
  --input:             oklch(1 0 0 / 12%);
  --ring:              oklch(0.62 0.19 260);

  /* Charts */
  --chart-1: oklch(0.62 0.19 260);  /* blue   */
  --chart-2: oklch(0.65 0.15 160);  /* teal   */
  --chart-3: oklch(0.60 0.18 300);  /* purple */
  --chart-4: oklch(0.70 0.15 60);   /* amber  */
  --chart-5: oklch(0.60 0.20 30);   /* red    */

  --radius: 0.625rem;   /* = 10px Basis-Radius */

  /* Sidebar */
  --sidebar:                    oklch(0.15 0.025 260);
  --sidebar-foreground:         oklch(0.93 0.01 250);
  --sidebar-primary:            oklch(0.62 0.19 260);
  --sidebar-primary-foreground: oklch(0.98 0 0);
  --sidebar-accent:             oklch(0.22 0.03 260);
  --sidebar-accent-foreground:  oklch(0.93 0.01 250);
  --sidebar-border:             oklch(1 0 0 / 10%);
  --sidebar-ring:               oklch(0.62 0.19 260);
}
```

**Charakteristik:**
- Hue 260 (kühles Blau) zieht sich durch Background, Cards, Borders → ruhiges, kohärentes Erscheinungsbild.
- Borders/Inputs als **transluzentes Weiß** (`oklch(1 0 0 / 10%)`) statt Volltonfarbe → funktioniert auf jeder Hintergrundabstufung.
- `--ring` = `--primary` → Focus-State immer blau.

## 5. Typografie & Fonts

Geladen via `next/font/google` in `app/layout.tsx`:

```tsx
import { Inter, JetBrains_Mono } from "next/font/google";

const inter = Inter({           variable: "--font-sans",        subsets: ["latin"] });
const jetbrainsMono = JetBrains_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
```

Aktivierung am `<html>`:
```tsx
<html
  lang="en"
  className={`${inter.variable} ${jetbrainsMono.variable} dark h-full antialiased`}
>
  <body className="min-h-full flex flex-col">{children}</body>
</html>
```

- **Sans / Heading**: Inter (`font-sans`, `font-heading`)
- **Mono**: JetBrains Mono (`font-mono`) — für Code, Monaco, Shiki
- `antialiased` global aktiv

## 6. Base Layer

```css
@layer base {
  * {
    @apply border-border outline-ring/50;
  }
  body {
    @apply bg-background text-foreground;
  }
  html {
    @apply font-sans;
  }
}
```

→ Jeder Border erbt `--border`, jeder Outline-Ring den Primary-Ton mit 50 % Alpha.

## 7. Radius-Skala

`--radius: 0.625rem` (10 px) ist die Quelle. Die Skala wird **multiplikativ** abgeleitet:

| Token | Faktor | Pixel |
|-------|--------|-------|
| `rounded-sm` | ×0.6 | 6 px |
| `rounded-md` | ×0.8 | 8 px |
| `rounded-lg` | ×1.0 | 10 px |
| `rounded-xl` | ×1.4 | 14 px |
| `rounded-2xl` | ×1.8 | 18 px |
| `rounded-3xl` | ×2.2 | 22 px |
| `rounded-4xl` | ×2.6 | 26 px |

Ein einziger Token tunt das gesamte Rundungsgefühl der App.

## 8. shadcn / `components.json`

```json
{
  "style": "base-nova",
  "rsc": true,
  "tsx": true,
  "tailwind": {
    "css": "app/globals.css",
    "baseColor": "neutral",
    "cssVariables": true,
    "prefix": ""
  },
  "iconLibrary": "lucide",
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "menuColor": "default",
  "menuAccent": "subtle"
}
```

Wichtig: shadcn-Komponenten werden hier **auf `@base-ui/react`** statt Radix gebaut (siehe `button.tsx` → `import { Button as ButtonPrimitive } from "@base-ui/react/button"`).

Aktive UI-Komponenten in `components/ui/`:
`tabs`, `card`, `sheet`, `scroll-area`, `label`, `navigation-menu`, `alert`, `dialog`, `badge`, `separator`, `button`, `select`, `textarea`, `input`.

## 9. Variant-Pattern (Beispiel `Button`)

Die Buttons demonstrieren das durchgängige Konventions-Set: `cva` + `data-slot` + `aria-*`-Selektoren + automatisches SVG-Sizing.

```ts
const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center rounded-lg border border-transparent bg-clip-padding text-sm font-medium whitespace-nowrap transition-all outline-none select-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 active:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:    "bg-primary text-primary-foreground [a]:hover:bg-primary/80",
        outline:    "border-border bg-background hover:bg-muted hover:text-foreground dark:border-input dark:bg-input/30 dark:hover:bg-input/50",
        secondary:  "bg-secondary text-secondary-foreground hover:bg-secondary/80",
        ghost:      "hover:bg-muted hover:text-foreground dark:hover:bg-muted/50",
        destructive:"bg-destructive/10 text-destructive hover:bg-destructive/20 focus-visible:border-destructive/40 focus-visible:ring-destructive/20",
        link:       "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default:  "h-8 gap-1.5 px-2.5",
        xs:       "h-6 gap-1 rounded-[min(var(--radius-md),10px)] px-2 text-xs",
        sm:       "h-7 gap-1 rounded-[min(var(--radius-md),12px)] px-2.5 text-[0.8rem]",
        lg:       "h-9 gap-1.5 px-2.5",
        icon:     "size-8",
        "icon-xs":"size-6 rounded-[min(var(--radius-md),10px)]",
        "icon-sm":"size-7 rounded-[min(var(--radius-md),12px)]",
        "icon-lg":"size-9",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  }
);
```

Konventionen, die hier sichtbar sind und konsequent gelten:
- Höhen sind **klein** (`h-6`/`h-7`/`h-8`/`h-9`) — kompaktes Tool-UI.
- `focus-visible:ring-3` mit `ring-ring/50` für sichtbaren, dezenten Fokus.
- `active:translate-y-px` als haptisches Mikro-Feedback.
- `[&_svg:not([class*='size-'])]:size-4` → Icons im Button werden automatisch auf 16 px geclampt, sofern nicht überschrieben.
- `aria-invalid` und `aria-expanded` werden direkt im Klassen-String adressiert (Base UI nutzt diese ARIA-Attribute).
- `data-slot="button"` für Slot-basiertes Styling von außen.

## 10. `cn`-Helper

Standard-Utility (in `lib/utils.ts`):

```ts
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

## 11. Layout-Konvention

```tsx
<html className="... dark h-full antialiased">
  <body className="min-h-full flex flex-col">{children}</body>
</html>
```

- Voll-Höhe (`h-full` / `min-h-full`) + Flex-Column → Editor-Apps mit Sidebar/Canvas füllen den Viewport.
- `dark` ist hart gesetzt; für Light-Mode-Support müsste man die `:root`-Variablen splitten.

## 12. Übernahme in ein neues Projekt — Checkliste

1. **Dependencies** (siehe `package.json`-Auszug): `tailwindcss@4`, `@tailwindcss/postcss`, `tw-animate-css`, `shadcn@4`, `@base-ui/react`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`, `tailwind-merge`.
2. `postcss.config.mjs` mit `@tailwindcss/postcss` anlegen.
3. `app/globals.css` aus diesem Projekt **1:1 kopieren** (Imports + `@theme inline` + `:root` + Base Layer).
4. `app/layout.tsx`: Inter + JetBrains Mono via `next/font/google`, `--font-sans` / `--font-geist-mono` Variablen, `<html className="... dark h-full antialiased">`.
5. `components.json` kopieren, dann `npx shadcn@latest add button card dialog ...` — die Komponenten landen in `components/ui/` und respektieren automatisch die Tokens.
6. `lib/utils.ts` mit `cn` anlegen.
7. Optional: `--radius` anpassen, um die gesamte Rundungs-Skala zu verschieben.
8. Optional: Hue 260 in den `oklch()`-Werten austauschen, um den Akzent auf eine andere Farbe zu drehen — alle Tokens (Primary, Ring, Sidebar, Chart-1) skalieren mit.

## 13. Was hier **nicht** enthalten ist

- **Kein Light-Mode** — `:root` ist die Dark-Variante. Für Theme-Switching müsste eine `.light`-Klasse mit eigenen OKLCH-Werten ergänzt werden.
- **Keine `tailwind.config.{js,ts}`** — Tailwind v4 wird vollständig über CSS getrieben.
- **Kein globales Reset über `globals.css` hinaus** — Tailwind Preflight + Base Layer reichen.
- **Styled-Components** wird nur punktuell für Rete.js-Nodes genutzt, nicht als Haupt-Styling-Pfad.
