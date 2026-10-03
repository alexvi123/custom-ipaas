import { describe, expect, it } from 'vitest'
import type { FieldDefinition } from '../../api/client'
import { buildFormSchema, defaultValues, toRequestValues } from './formSchema'

function field(overrides: Partial<FieldDefinition> & Pick<FieldDefinition, 'key' | 'label' | 'type'>): FieldDefinition {
  return { required: false, options: [], dynamicOptions: false, allowCustomValue: false, dependsOn: [], ...overrides }
}

function errorFor(fields: FieldDefinition[], values: Record<string, unknown>, key: string): string | undefined {
  const result = buildFormSchema(fields).safeParse(values)
  return result.success ? undefined : result.error.issues.find((issue) => issue.path[0] === key)?.message
}

describe('buildFormSchema', () => {
  it('requires required fields', () => {
    const fields = [field({ key: 'url', label: 'URL', type: 'STRING', required: true })]

    expect(errorFor(fields, { url: '  ' }, 'url')).toBe('Required')
    expect(errorFor(fields, { url: 'https://x' }, 'url')).toBeUndefined()
  })

  it('allows empty optional fields', () => {
    const fields = [field({ key: 'count', label: 'Count', type: 'NUMBER' })]

    expect(errorFor(fields, { count: '' }, 'count')).toBeUndefined()
  })

  it('checks numbers and JSON', () => {
    const fields = [
      field({ key: 'count', label: 'Count', type: 'NUMBER' }),
      field({ key: 'payload', label: 'Payload', type: 'JSON' }),
    ]

    expect(errorFor(fields, { count: 'abc', payload: '{}' }, 'count')).toBe('Must be a number')
    expect(errorFor(fields, { count: '1', payload: '{oops' }, 'payload')).toBe('Must be valid JSON')
  })

  it('restricts static selects to their options, but not dynamic or custom ones', () => {
    const fixed = field({ key: 'm', label: 'M', type: 'SELECT', options: [{ value: 'GET', label: 'GET' }] })
    const custom = field({ key: 'c', label: 'C', type: 'SELECT', dynamicOptions: true, allowCustomValue: true })

    expect(errorFor([fixed], { m: 'TRACE' }, 'm')).toBe('Must be one of: GET')
    expect(errorFor([custom], { c: '12345' }, 'c')).toBeUndefined()
  })
})

describe('defaultValues', () => {
  it('uses defaults, empty strings and false for checkboxes', () => {
    const fields = [
      field({ key: 'method', label: 'Method', type: 'SELECT', defaultValue: 'GET' }),
      field({ key: 'url', label: 'URL', type: 'STRING' }),
      field({ key: 'flag', label: 'Flag', type: 'BOOLEAN' }),
    ]

    expect(defaultValues(fields)).toEqual({ method: 'GET', url: '', flag: false })
  })
})

describe('toRequestValues', () => {
  it('drops empty optional fields and converts numbers', () => {
    const fields = [
      field({ key: 'title', label: 'Title', type: 'STRING' }),
      field({ key: 'notes', label: 'Notes', type: 'TEXT' }),
      field({ key: 'count', label: 'Count', type: 'NUMBER' }),
      field({ key: 'flag', label: 'Flag', type: 'BOOLEAN' }),
    ]

    expect(toRequestValues(fields, { title: ' Hi ', notes: '', count: '3', flag: false })).toEqual({
      title: 'Hi',
      count: 3,
      flag: false,
    })
  })
})
