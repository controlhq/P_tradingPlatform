interface PaginationProps {
  /** Zero-based, like the backend. */
  page: number;
  totalPages: number;
  onChange(page: number): void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Stronicowanie">
      <button className="btn" type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>
        ← Poprzednia
      </button>
      <span className="muted">
        Strona {page + 1} z {totalPages}
      </span>
      <button className="btn" type="button" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Następna →
      </button>
    </nav>
  );
}
