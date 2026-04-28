import type {
	AddGroup,
	AddGroupItemsRequest,
	AddGroupItemsResponse,
	AddRecord,
	AddSpace,
	AddSpaceMember,
	AddTask,
	FeedPage,
	Group,
	Record,
	Space,
	SpaceMember,
	Task,
	UpdateGroup,
	UpdateRecord,
	UpdateSpace,
	UpdateTask
} from './types';
import { cacheGet, cachePut, cacheDelete, cacheClear } from './cache';
import { enqueue, type ResourceKind } from './sync.svelte';

const BASE = import.meta.env.VITE_API_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
	const res = await fetch(`${BASE}${path}`, {
		...init,
		headers: { 'Content-Type': 'application/json', ...init?.headers }
	});
	if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
	if (res.status === 204) return undefined as T;
	return res.json() as Promise<T>;
}

// Offline-safe mutation: tries network; if offline, queues for replay.
// Creates (method=POST with no resourceId) are not queueable — caller gets an error when offline.
async function mutate<T>(
	resource: ResourceKind,
	resourceId: string | null,
	path: string,
	init: RequestInit
): Promise<T> {
	if (typeof navigator !== 'undefined' && !navigator.onLine) {
		const method = (init.method ?? 'GET') as import('./sync.svelte').MutationMethod;
		if (method === 'POST') {
			throw new Error('Cannot create new items while offline');
		}
		await enqueue(resource, resourceId, method, path, init.body ? JSON.parse(init.body as string) : null);
		// Return a sentinel so callers can optimistically update UI.
		// Callers must handle undefined — mutations while offline return nothing.
		return undefined as T;
	}
	return request<T>(path, init);
}

// Stale-while-revalidate: call onCached immediately if cache hit, then fetch and call onFresh.
// If cache hit and network fails, swallows error — stale data stays visible.
// If no cache and network fails, rethrows so callers can show error state.
async function swr<T>(
	store: Parameters<typeof cacheGet>[0],
	key: string,
	fetcher: () => Promise<T>,
	onCached: (data: T) => void,
	onFresh: (data: T) => void
): Promise<T> {
	let hadCache = false;
	if (typeof window !== 'undefined') {
		const cached = await cacheGet<T>(store, key);
		if (cached !== undefined) { onCached(cached); hadCache = true; }
	}
	try {
		const fresh = await fetcher();
		if (typeof window !== 'undefined') {
			await cachePut(store, key, fresh);
		}
		onFresh(fresh);
		return fresh;
	} catch (e) {
		if (hadCache) return undefined as T; // stale data already shown, stay silent
		throw e;
	}
}

export const api = {
	// Tasks
	getTasks: (onCached?: (d: Task[]) => void, onFresh?: (d: Task[]) => void) =>
		swr('tasks', 'list', () => request<Task[]>('/tasks'), onCached ?? (() => {}), onFresh ?? (() => {})),
	getTask: (id: string, onCached?: (d: Task) => void, onFresh?: (d: Task) => void) =>
		swr('tasks', id, () => request<Task>(`/tasks/${id}`), onCached ?? (() => {}), onFresh ?? (() => {})),
	createTask: async (body: AddTask) => {
		const t = await request<Task>('/tasks', { method: 'POST', body: JSON.stringify(body) });
		await cacheClear('tasks');
		return t;
	},
	updateTask: async (id: string, body: UpdateTask) => {
		const t = await mutate<Task>('tasks', id, `/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(body) });
		if (t) { await cachePut('tasks', id, t); await cacheDelete('tasks', 'list'); }
		return t;
	},
	deleteTask: async (id: string) => {
		await mutate<void>('tasks', id, `/tasks/${id}`, { method: 'DELETE' });
		await cacheClear('tasks');
	},

	// Records
	getRecords: (onCached?: (d: Record[]) => void, onFresh?: (d: Record[]) => void) =>
		swr('records', 'list', () => request<Record[]>('/records'), onCached ?? (() => {}), onFresh ?? (() => {})),
	getRecord: (id: string, onCached?: (d: Record) => void, onFresh?: (d: Record) => void) =>
		swr('records', id, () => request<Record>(`/records/${id}`), onCached ?? (() => {}), onFresh ?? (() => {})),
	createRecord: async (body: AddRecord) => {
		const r = await request<Record>('/records', { method: 'POST', body: JSON.stringify(body) });
		await cacheClear('records');
		return r;
	},
	updateRecord: async (id: string, body: UpdateRecord) => {
		const r = await mutate<Record>('records', id, `/records/${id}`, { method: 'PATCH', body: JSON.stringify(body) });
		if (r) { await cachePut('records', id, r); await cacheDelete('records', 'list'); }
		return r;
	},
	deleteRecord: async (id: string) => {
		await mutate<void>('records', id, `/records/${id}`, { method: 'DELETE' });
		await cacheClear('records');
	},

	// Groups
	getGroups: (onCached?: (d: Group[]) => void, onFresh?: (d: Group[]) => void) =>
		swr('groups', 'list', () => request<Group[]>('/groups'), onCached ?? (() => {}), onFresh ?? (() => {})),
	getGroup: (id: string, onCached?: (d: Group) => void, onFresh?: (d: Group) => void) =>
		swr('groups', id, () => request<Group>(`/groups/${id}`), onCached ?? (() => {}), onFresh ?? (() => {})),
	createGroup: async (body: AddGroup) => {
		const g = await request<Group>('/groups', { method: 'POST', body: JSON.stringify(body) });
		await cacheClear('groups');
		return g;
	},
	updateGroup: async (id: string, body: UpdateGroup) => {
		const g = await mutate<Group>('groups', id, `/groups/${id}`, { method: 'PATCH', body: JSON.stringify(body) });
		if (g) { await cachePut('groups', id, g); await cacheDelete('groups', 'list'); }
		return g;
	},
	deleteGroup: async (id: string) => {
		await mutate<void>('groups', id, `/groups/${id}`, { method: 'DELETE' });
		await cacheClear('groups');
	},
	addGroupItems: async (id: string, body: AddGroupItemsRequest) => {
		const r = await request<AddGroupItemsResponse>(`/groups/${id}/items`, { method: 'POST', body: JSON.stringify(body) });
		await cacheClear('groups');
		await cacheClear('tasks');
		await cacheClear('records');
		return r;
	},

	// Spaces
	getSpaces: (onCached?: (d: Space[]) => void, onFresh?: (d: Space[]) => void) =>
		swr('spaces', 'list', () => request<Space[]>('/spaces'), onCached ?? (() => {}), onFresh ?? (() => {})),
	getSpace: (id: string, onCached?: (d: Space) => void, onFresh?: (d: Space) => void) =>
		swr('spaces', id, () => request<Space>(`/spaces/${id}`), onCached ?? (() => {}), onFresh ?? (() => {})),
	createSpace: async (body: AddSpace) => {
		const s = await request<Space>('/spaces', { method: 'POST', body: JSON.stringify(body) });
		await cacheClear('spaces');
		return s;
	},
	updateSpace: async (id: string, body: UpdateSpace) => {
		const s = await mutate<Space>('spaces', id, `/spaces/${id}`, { method: 'PATCH', body: JSON.stringify(body) });
		if (s) { await cachePut('spaces', id, s); await cacheDelete('spaces', 'list'); }
		return s;
	},
	deleteSpace: async (id: string) => {
		await mutate<void>('spaces', id, `/spaces/${id}`, { method: 'DELETE' });
		await cacheClear('spaces');
	},
	listSpaceMembers: (id: string) => request<SpaceMember[]>(`/spaces/${id}/members`),
	addSpaceMember: (id: string, body: AddSpaceMember) =>
		request<SpaceMember>(`/spaces/${id}/members`, { method: 'POST', body: JSON.stringify(body) }),
	removeSpaceMember: (id: string, userId: string) =>
		request<void>(`/spaces/${id}/members/${userId}`, { method: 'DELETE' }),

	// Feed
	getFeed: (params?: { limit?: number; offset?: number; spaceId?: string | null }, onCached?: (d: FeedPage) => void, onFresh?: (d: FeedPage) => void) => {
		const q = new URLSearchParams();
		if (params?.limit != null) q.set('limit', String(params.limit));
		if (params?.offset != null) q.set('offset', String(params.offset));
		if (params?.spaceId) q.set('spaceId', params.spaceId);
		const qs = q.toString();
		const key = `feed:${qs}`;
		return swr('feed', key, () => request<FeedPage>(`/feed${qs ? '?' + qs : ''}`), onCached ?? (() => {}), onFresh ?? (() => {}));
	}
};
