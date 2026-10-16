import { useEffect, useRef, useSyncExternalStore } from 'react';
import { GRID, meshPalette, type Rgb } from '@/lib/meshPalette';
import styles from './MeshBackdrop.module.css';

/**
 * The canvas is tiny and the GPU scales it to the screen: its blobs are
 * already soft, so bilinear filtering is all the blur they need. A frame is
 * GRID² radial gradients over 64×40 pixels, drawn at most 30 times a second.
 */
const WIDTH = 64;
const HEIGHT = 40;
const FRAME_MS = 1000 / 30;
const FADE_MS = 1200;
/** Before the artwork is read, and when it can't be: a neutral dark grey. */
const FALLBACK: Rgb[] = Array.from({ length: GRID * GRID }, () => [52, 52, 56]);

const reducedMotion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      reducedMotion?.addEventListener('change', onChange);
      return () => reducedMotion?.removeEventListener('change', onChange);
    },
    () => reducedMotion?.matches ?? false,
  );
}

/**
 * Each cell's blob: where it sits and how it drifts. The grid is turned a
 * quarter: the colours keep their neighbours and proportions, but don't line
 * up column for column with the artwork shown over them, which otherwise reads
 * as a blurred reflection.
 */
const BLOBS = Array.from({ length: GRID * GRID }, (_, i) => {
  const row = Math.floor(i / GRID);
  const col = i % GRID;
  const spread = (n: number) => -0.1 + ((n + 0.5) / GRID) * 1.2;
  // A fixed pseudo-random phase per cell, so every track drifts the same way.
  const seed = (n: number) => ((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1 + 1) % 1;
  return {
    x: spread(GRID - 1 - row),
    y: spread(col),
    phase: [seed(1), seed(2), seed(3)].map((v) => v * Math.PI * 2) as [number, number, number],
    speed: [0.07 + seed(4) * 0.06, 0.06 + seed(5) * 0.06, 0.05 + seed(6) * 0.04] as [number, number, number],
  };
});

function mix(from: Rgb[], to: Rgb[], k: number): Rgb[] {
  return to.map((c, i) => c.map((v, j) => from[i]![j]! + (v - from[i]![j]!) * k) as Rgb);
}

function average(cells: Rgb[]): Rgb {
  const sum = cells.reduce<Rgb>((a, c) => [a[0] + c[0], a[1] + c[1], a[2] + c[2]], [0, 0, 0]);
  return sum.map((v) => v / cells.length) as Rgb;
}

const css = ([r, g, b]: Rgb, a: number) => `rgb(${r | 0} ${g | 0} ${b | 0} / ${a})`;

function paint(context: CanvasRenderingContext2D, cells: Rgb[], seconds: number) {
  context.fillStyle = css(average(cells), 1);
  context.fillRect(0, 0, WIDTH, HEIGHT);
  const reach = Math.max(WIDTH, HEIGHT) * 0.62;
  BLOBS.forEach((blob, i) => {
    const [px, py, pr] = blob.phase;
    const [sx, sy, sr] = blob.speed;
    const x = (blob.x + 0.16 * Math.sin(seconds * sx + px)) * WIDTH;
    const y = (blob.y + 0.2 * Math.cos(seconds * sy + py)) * HEIGHT;
    const radius = reach * (0.85 + 0.2 * Math.sin(seconds * sr + pr));
    const color = cells[i]!;
    const gradient = context.createRadialGradient(x, y, 0, x, y, radius);
    gradient.addColorStop(0, css(color, 0.9));
    gradient.addColorStop(0.55, css(color, 0.35));
    gradient.addColorStop(1, css(color, 0));
    context.fillStyle = gradient;
    context.fillRect(0, 0, WIDTH, HEIGHT);
  });
}

/**
 * The full-screen player's background: the artwork's colours as a slowly
 * drifting mesh gradient (report §7, "the artwork, blurred and animated").
 * It moves only while the music plays and motion is allowed, cross-fades to
 * each new track's colours, and draws nothing while the tab is hidden.
 */
export function MeshBackdrop({ artworkUrl, playing }: { artworkUrl: string | null; playing: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const still = useReducedMotion();
  const animate = playing && !still;
  // Kept outside React: the frame loop reads and writes these.
  const state = useRef({ from: FALLBACK, to: FALLBACK, fadeStart: -Infinity, seconds: 0, wake: () => {} });

  useEffect(() => {
    if (!artworkUrl) return;
    let live = true;
    meshPalette(artworkUrl)
      .then((cells) => {
        if (!live) return;
        const s = state.current;
        const now = performance.now();
        // The first colours show at once; later tracks cross-fade from whatever is on screen.
        s.from = s.fadeStart === -Infinity ? cells : mix(s.from, s.to, Math.min(1, (now - s.fadeStart) / FADE_MS));
        s.to = cells;
        s.fadeStart = now;
        s.wake();
      })
      .catch(() => {
        // An unreadable image keeps the colours already showing.
      });
    return () => {
      live = false;
    };
  }, [artworkUrl]);

  useEffect(() => {
    const context = canvas.current?.getContext('2d', { alpha: false });
    if (!context) return;
    const s = state.current;
    let frame = 0;
    let last = performance.now();
    let drawn = -Infinity;
    const tick = (now: number) => {
      frame = 0;
      if (animate) s.seconds += Math.min(now - last, 100) / 1000;
      last = now;
      const fade = Math.min(1, (now - s.fadeStart) / FADE_MS);
      if (now - drawn >= FRAME_MS - 1) {
        drawn = now;
        // Ease out, so the old colours let go quickly and the new ones settle.
        paint(context, fade < 1 ? mix(s.from, s.to, 1 - (1 - fade) ** 3) : s.to, s.seconds);
      }
      if (animate || fade < 1) frame = requestAnimationFrame(tick);
    };
    s.wake = () => {
      if (!frame) {
        last = performance.now();
        frame = requestAnimationFrame(tick);
      }
    };
    s.wake();
    return () => {
      cancelAnimationFrame(frame);
      s.wake = () => {};
    };
  }, [animate]);

  return (
    <div className={styles.backdrop} aria-hidden="true">
      <canvas ref={canvas} className={styles.canvas} width={WIDTH} height={HEIGHT} />
    </div>
  );
}
