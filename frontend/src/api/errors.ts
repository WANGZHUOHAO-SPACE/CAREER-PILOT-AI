/** Shared safe HTTP messages for both Axios and the authenticated SSE fetch. */
export function apiErrorMessage(status: number, serverMessage?: unknown): string {
  const messages: Record<number, string> = {
    403: 'Access denied. You do not have permission to perform this action.',
    404: 'The requested resource was not found.',
    409: 'This action conflicts with the current state. Please refresh and try again.',
    429: 'Too many requests / 请求过于频繁，请稍后再试。',
    500: 'The service could not process this request. Please try again.',
    503: 'AI or database service is temporarily unavailable. Please try again later.'
  }
  if (messages[status]) return messages[status]
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage
  return status === 401 ? 'Session expired. Please sign in again.' : `Request failed (${status}). Please try again.`
}
