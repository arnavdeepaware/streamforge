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
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        type="button"
      >
        Previous
      </button>
      <span aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        type="button"
      >
        Next
      </button>
    </nav>
  );
}
