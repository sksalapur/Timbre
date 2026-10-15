import { PrefetchLink } from '@/components/PrefetchLink';
import type { CategoryGroup } from '@/data/music/types';
import styles from './CategoryTiles.module.css';
import { browseHref } from './links';

/**
 * Browse Categories bricks (report §6): 16:9 tiles, radius 10, the title at
 * the bottom left. Coloured from YouTube's own stripe colour per mood/genre.
 */
export function CategoryTiles({ groups }: { groups: CategoryGroup[] }) {
  return (
    <>
      {groups.map((group) => (
        <section key={group.title} className={styles.group} aria-label={group.title}>
          <h2 className={styles.heading}>{group.title}</h2>
          <ul className={styles.grid}>
            {group.categories.map((category) => (
              <li key={`${category.title}-${category.link.params}`}>
                <PrefetchLink
                  to={browseHref(category.link)}
                  className={styles.tile}
                  style={{ '--tile-color': category.color } as React.CSSProperties}
                >
                  <span className={styles.title}>{category.title}</span>
                </PrefetchLink>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
