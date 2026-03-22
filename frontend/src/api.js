const BASE = '/api';
const pendingRequests = new Map();
const RETRY_DELAY_MS = 200;
const MAX_GET_RETRIES = 1;

export const authEvents = {
    unauthorized: 'firstapi:unauthorized',
};

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function shouldRetry(method, status, hasTransportError) {
    return method === 'GET' && (hasTransportError || status >= 500);
}

function emitUnauthorized(url) {
    window.dispatchEvent(new CustomEvent(authEvents.unauthorized, { detail: { url } }));
}

async function executeRequest(url, options, method, meta) {
    let attempt = 0;
    const requestMeta = meta || {};

    while (true) {
        try {
            const res = await fetch(BASE + url, {
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                    ...(options.headers || {}),
                },
                ...options,
            });

            const text = await res.text();
            let json = null;
            if (text) {
                try {
                    json = JSON.parse(text);
                } catch {
                    if (attempt < MAX_GET_RETRIES && shouldRetry(method, res.status, true)) {
                        attempt += 1;
                        await sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    if (res.status === 401 && !requestMeta.allowUnauthorized && !requestMeta.skipUnauthorizedEvent) {
                        emitUnauthorized(url);
                    }
                    throw new Error(res.ok ? '请求失败' : `请求失败（HTTP ${res.status}）`);
                }
            }

            if (!res.ok || !json || !json.success) {
                const message = json?.message || `请求失败（HTTP ${res.status}）`;
                if (res.status === 401 && !requestMeta.allowUnauthorized && !requestMeta.skipUnauthorizedEvent) {
                    emitUnauthorized(url);
                }
                if (attempt < MAX_GET_RETRIES && shouldRetry(method, res.status, false)) {
                    attempt += 1;
                    await sleep(RETRY_DELAY_MS);
                    continue;
                }
                throw new Error(message);
            }

            return json.data;
        } catch (error) {
            if (error.name === 'AbortError') {
                throw error;
            }
            if (attempt < MAX_GET_RETRIES && shouldRetry(method, 0, true)) {
                attempt += 1;
                await sleep(RETRY_DELAY_MS);
                continue;
            }
            throw error;
        }
    }
}

async function request(url, options = {}, meta = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const bodyKey = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
    const requestKey = `${method}:${url}:${bodyKey}`;

    if (pendingRequests.has(requestKey) && method === 'GET') {
        return pendingRequests.get(requestKey);
    }

    const pending = executeRequest(url, options, method, meta).finally(() => {
        pendingRequests.delete(requestKey);
    });

    if (method === 'GET') {
        pendingRequests.set(requestKey, pending);
    }
    return pending;
}

export const api = {
    get: (url, meta) => request(url, {}, meta),
    post: (url, body, meta) => request(url, { method: 'POST', body: JSON.stringify(body) }, meta),
    put: (url, body, meta) => request(url, { method: 'PUT', body: JSON.stringify(body) }, meta),
    del: (url, meta) => request(url, { method: 'DELETE' }, meta),
};

