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

export const api = {
	getTasks: () => request<Task[]>('/tasks'),
	getTask: (id: string) => request<Task>(`/tasks/${id}`),
	createTask: (body: AddTask) =>
		request<Task>('/tasks', { method: 'POST', body: JSON.stringify(body) }),
	updateTask: (id: string, body: UpdateTask) =>
		request<Task>(`/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
	deleteTask: (id: string) => request<void>(`/tasks/${id}`, { method: 'DELETE' }),

	getRecords: () => request<Record[]>('/records'),
	getRecord: (id: string) => request<Record>(`/records/${id}`),
	createRecord: (body: AddRecord) =>
		request<Record>('/records', { method: 'POST', body: JSON.stringify(body) }),
	updateRecord: (id: string, body: UpdateRecord) =>
		request<Record>(`/records/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
	deleteRecord: (id: string) => request<void>(`/records/${id}`, { method: 'DELETE' }),

	getGroups: () => request<Group[]>('/groups'),
	getGroup: (id: string) => request<Group>(`/groups/${id}`),
	createGroup: (body: AddGroup) =>
		request<Group>('/groups', { method: 'POST', body: JSON.stringify(body) }),
	updateGroup: (id: string, body: UpdateGroup) =>
		request<Group>(`/groups/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
	deleteGroup: (id: string) => request<void>(`/groups/${id}`, { method: 'DELETE' }),
	addGroupItems: (id: string, body: AddGroupItemsRequest) =>
		request<AddGroupItemsResponse>(`/groups/${id}/items`, { method: 'POST', body: JSON.stringify(body) }),

	getSpaces: () => request<Space[]>('/spaces'),
	getSpace: (id: string) => request<Space>(`/spaces/${id}`),
	createSpace: (body: AddSpace) =>
		request<Space>('/spaces', { method: 'POST', body: JSON.stringify(body) }),
	updateSpace: (id: string, body: UpdateSpace) =>
		request<Space>(`/spaces/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
	deleteSpace: (id: string) => request<void>(`/spaces/${id}`, { method: 'DELETE' }),
	listSpaceMembers: (id: string) => request<SpaceMember[]>(`/spaces/${id}/members`),
	addSpaceMember: (id: string, body: AddSpaceMember) =>
		request<SpaceMember>(`/spaces/${id}/members`, { method: 'POST', body: JSON.stringify(body) }),
	removeSpaceMember: (id: string, userId: string) =>
		request<void>(`/spaces/${id}/members/${userId}`, { method: 'DELETE' }),

	getFeed: (params?: { limit?: number; offset?: number }) => {
		const q = new URLSearchParams();
		if (params?.limit != null) q.set('limit', String(params.limit));
		if (params?.offset != null) q.set('offset', String(params.offset));
		const qs = q.toString();
		return request<FeedPage>(`/feed${qs ? '?' + qs : ''}`);
	}
};
