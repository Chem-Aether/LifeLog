const MEDIA_BASE_URL = import.meta.env.VITE_MEDIA_BASE_URL?.trim().replace(/\/+$/, '')

if (!MEDIA_BASE_URL) throw new Error('Missing environment variable: VITE_MEDIA_BASE_URL')

export const resolveMediaUrl = path => path ? new URL(path, `${MEDIA_BASE_URL}/`).toString() : ''
