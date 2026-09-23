export function Pagination({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Results pagination" className="pagination">
      <button
        className="button button--secondary"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        type="button"
      >
        <ChevronLeft aria-hidden="true" size={17} /> Previous
      </button>
      <span aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        className="button button--secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        type="button"
      >
        Next <ChevronRight aria-hidden="true" size={17} />
      </button>
    </nav>
  );
}
import { ChevronLeft, ChevronRight } from 'lucide-react';
