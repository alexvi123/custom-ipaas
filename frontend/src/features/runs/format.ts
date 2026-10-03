export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

export function formatTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}
