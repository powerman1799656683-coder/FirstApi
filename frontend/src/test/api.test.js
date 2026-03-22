import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

const dispatchedEvents = [];
const originalDispatchEvent = window.dispatchEvent.bind(window);

window.dispatchEvent = (event) => {
    dispatchedEvents.push(event);
    return originalDispatchEvent(event);
};

let api;
let authEvents;

beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    dispatchedEvents.length = 0;

    const module = await import('../api.js');
    api = module.api;
    authEvents = module.authEvents;
});

afterAll(() => {
    window.dispatchEvent = originalDispatchEvent;
});

function jsonResponse(data, status = 200) {
    return Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        text: () => Promise.resolve(JSON.stringify(data)),
    });
}

function errorResponse(status, message = '请求失败') {
    return Promise.resolve({
        ok: false,
        status,
        text: () => Promise.resolve(JSON.stringify({ success: false, message })),
    });
}

describe('api module', () => {
    describe('api.get', () => {
        it('returns the data field for successful responses', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: { id: 1 } }));

            const result = await api.get('/test');

            expect(result).toEqual({ id: 1 });
            expect(mockFetch).toHaveBeenCalledWith(
                '/api/test',
                expect.objectContaining({
                    credentials: 'include',
                })
            );
        });

        it('does not duplicate /api for already-prefixed GET urls', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: [] }));

            await api.get('/api/admin/model-pricing');

            expect(mockFetch).toHaveBeenCalledWith(
                '/api/admin/model-pricing',
                expect.objectContaining({
                    credentials: 'include',
                })
            );
        });

        it('throws the API message when success is false', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: false, message: '操作失败' }));

            await expect(api.get('/fail')).rejects.toThrow('操作失败');
        });

        it('retries GET once for 5xx responses', async () => {
            mockFetch
                .mockReturnValueOnce(errorResponse(500, '服务端错误'))
                .mockReturnValueOnce(jsonResponse({ success: true, data: 'ok' }));

            const result = await api.get('/retry');

            expect(result).toBe('ok');
            expect(mockFetch).toHaveBeenCalledTimes(2);
        });

        it('throws after the retry also fails', async () => {
            mockFetch
                .mockReturnValueOnce(errorResponse(500, '错误1'))
                .mockReturnValueOnce(errorResponse(500, '错误2'));

            await expect(api.get('/retry-fail')).rejects.toThrow('错误2');
        });

        it('dispatches the unauthorized event for 401 responses', async () => {
            mockFetch.mockReturnValue(errorResponse(401, '未授权'));

            await expect(api.get('/protected')).rejects.toThrow();

            const unauthorizedEvents = dispatchedEvents.filter(
                (event) => event.type === authEvents.unauthorized
            );
            expect(unauthorizedEvents).toHaveLength(1);
        });

        it('does not dispatch unauthorized when explicitly suppressed', async () => {
            mockFetch.mockReturnValue(errorResponse(401, '未授权'));

            await expect(
                api.get('/auth/session', { allowUnauthorized: true, skipUnauthorizedEvent: true })
            ).rejects.toThrow();

            const unauthorizedEvents = dispatchedEvents.filter(
                (event) => event.type === authEvents.unauthorized
            );
            expect(unauthorizedEvents).toHaveLength(0);
        });
    });

    describe('api.post', () => {
        it('sends POST requests with a JSON body', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: { created: true } }));

            const result = await api.post('/create', { name: 'test' });

            expect(result).toEqual({ created: true });

            const [url, options] = mockFetch.mock.calls[0];
            expect(url).toBe('/api/create');
            expect(options.method).toBe('POST');
            expect(options.body).toBe(JSON.stringify({ name: 'test' }));
        });

        it('does not duplicate /api for already-prefixed POST urls', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: { created: true } }));

            await api.post('/api/admin/model-pricing', { name: 'test' });

            const [url, options] = mockFetch.mock.calls[0];
            expect(url).toBe('/api/admin/model-pricing');
            expect(options.method).toBe('POST');
            expect(options.body).toBe(JSON.stringify({ name: 'test' }));
        });

        it('does not retry POST requests', async () => {
            mockFetch.mockReturnValue(errorResponse(500, '服务端错误'));

            await expect(api.post('/create', {})).rejects.toThrow('服务端错误');
            expect(mockFetch).toHaveBeenCalledTimes(1);
        });
    });

    describe('api.put', () => {
        it('sends PUT requests', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: { updated: true } }));

            const result = await api.put('/update/1', { name: 'new' });

            expect(result).toEqual({ updated: true });

            const [url, options] = mockFetch.mock.calls[0];
            expect(url).toBe('/api/update/1');
            expect(options.method).toBe('PUT');
        });
    });

    describe('api.del', () => {
        it('sends DELETE requests', async () => {
            mockFetch.mockReturnValue(jsonResponse({ success: true, data: true }));

            const result = await api.del('/delete/1');

            expect(result).toBe(true);

            const [url, options] = mockFetch.mock.calls[0];
            expect(url).toBe('/api/delete/1');
            expect(options.method).toBe('DELETE');
        });
    });

    describe('error handling', () => {
        it('rethrows AbortError without retrying', async () => {
            const abortError = new DOMException('The operation was aborted.', 'AbortError');
            mockFetch.mockRejectedValue(abortError);

            await expect(api.get('/abort-test')).rejects.toThrow('The operation was aborted.');
            expect(mockFetch).toHaveBeenCalledTimes(1);
        });

        it('retries GET requests after transport errors', async () => {
            mockFetch
                .mockRejectedValueOnce(new TypeError('Failed to fetch'))
                .mockReturnValueOnce(jsonResponse({ success: true, data: 'recovered' }));

            const result = await api.get('/network-error');

            expect(result).toBe('recovered');
            expect(mockFetch).toHaveBeenCalledTimes(2);
        });

        it('retries GET requests when the response body is not valid JSON', async () => {
            mockFetch
                .mockReturnValueOnce(Promise.resolve({
                    ok: true,
                    status: 200,
                    text: () => Promise.resolve('not json'),
                }))
                .mockReturnValueOnce(jsonResponse({ success: true, data: 'ok' }));

            const result = await api.get('/bad-json');

            expect(result).toBe('ok');
            expect(mockFetch).toHaveBeenCalledTimes(2);
        });

        it('throws a fallback HTTP error when a non-ok response has no body', async () => {
            mockFetch.mockReturnValue(Promise.resolve({
                ok: false,
                status: 403,
                text: () => Promise.resolve(''),
            }));

            await expect(api.get('/forbidden')).rejects.toThrow('HTTP 403');
        });
    });
});
