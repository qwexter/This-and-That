import type { AddTask, AddRecord, Record, Task, UpdateTask, UpdateRecord } from './types';

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
	deleteRecord: (id: string) => request<void>(`/records/${id}`, { method: 'DELETE' })
};
