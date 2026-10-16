import type { CSSProperties } from 'react';
import styles from './Skeleton.module.css';

interface BlockProps {
  width?: CSSProperties['width'];
  height?: CSSProperties['height'];
  radius?: CSSProperties['borderRadius'];
  aspectRatio?: CSSProperties['aspectRatio'];
  className?: string;
  style?: CSSProperties;
}

/** A placeholder box with a shimmer. Purely decorative, so hidden from AT. */
export function SkeletonBlock({ width, height, radius, aspectRatio, className, style }: BlockProps) {
  return (
    <div
      aria-hidden="true"
      className={className ? `${styles.block} ${className}` : styles.block}
      style={{ width, height, borderRadius: radius, aspectRatio, ...style }}
    />
  );
}

interface LineProps {
  /** The text style's line height, so the row is exactly as tall as the text it stands in for. */
  lineHeight: number;
  /** Roughly the font size: the bar's thickness. */
  fontSize: number;
  width: CSSProperties['width'];
  style?: CSSProperties;
}

/** A single line of text in waiting. */
export function SkeletonLine({ lineHeight, fontSize, width, style }: LineProps) {
  return (
    <div aria-hidden="true" className={styles.line} style={{ height: lineHeight, ...style }}>
      <SkeletonBlock width={width} height={Math.round(fontSize * 0.75)} radius={3} />
    </div>
  );
}
