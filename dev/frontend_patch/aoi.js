import { getNewBackendJSON } from '@/api/manage'

export function getAiObservationSummary(params) {
    return getNewBackendJSON('/admin/ai/observations/summary', params)
}

export function getAiObservationList(params) {
    return getNewBackendJSON('/admin/ai/observations', params)
}

export function getAiObservationDetail(requestId) {
    return getNewBackendJSON(`/admin/ai/observations/${requestId}`, {})
}
