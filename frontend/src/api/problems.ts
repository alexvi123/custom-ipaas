export class FieldErrors extends Error {
  readonly errors: Record<string, string>

  constructor(errors: Record<string, string>) {
    super('Some fields are invalid')
    this.errors = errors
  }
}

export function problemDetail(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') {
    return body.detail
  }
  return undefined
}

export function fieldErrors(body: unknown): Record<string, string> | undefined {
  if (typeof body !== 'object' || body === null || !('errors' in body)) return undefined
  const errors = body.errors
  if (typeof errors !== 'object' || errors === null) return undefined
  return Object.fromEntries(Object.entries(errors).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
}
