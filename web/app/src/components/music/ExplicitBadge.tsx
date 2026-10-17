/** The boxed "E" (report §2: 10×16, 4px after the title). Drawn for BitChord. */
export function ExplicitBadge() {
  return (
    <span
      className="ms-1 inline-grid size-2.5 shrink-0 place-items-center rounded-[2px] bg-secondary align-[1px] type-[700_7.5px/1] text-page"
      role="img"
      aria-label="Explicit"
    >
      E
    </span>
  );
}
