import { useParams } from 'react-router';
import { ArtistSkeleton } from '@/skeletons/PageSkeletons';

/** Phase 4 fills this with YouTube Music data; until then it shows its loading state. */
export default function ArtistPage() {
  const { id } = useParams();
  return (
    <div data-id={id}>
      <ArtistSkeleton />
    </div>
  );
}
