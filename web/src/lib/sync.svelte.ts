import { cacheClear } from './cache';

export type MutationMethod = 'POST' | 'PATCH' | 'DELETE';
export type ResourceKind = 'tasks' | 'records' | 'groups' | 'spaces';

export interface QueuedMutation {
	id: string;
	timestamp: number;
	resource: ResourceKind;
	resourceId: string | null; // null for creates
	method: MutationMethod;
	url: string;
	body: string | null;
	retries: number;
}

// ── IDB helpers scoped to mutation-queue ────────────────────────────────────

async function queueDB(): Promise<IDBDatabase> {
	return new Promise((resolve, reject) => {
		const req = indexedDB.open('tat-cache', 2);
		req.onsuccess = () => resolve(req.result);
		req.onerror = () => reject(req.error);
	});
}

async function queueGetAll(): Promise<QueuedMutation[]> {
	try {
		const db = await queueDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction('mutation-queue', 'readonly');
			const req = tx.objectStore('mutation-queue').getAll();
			req.onsuccess = () => resolve((req.result as QueuedMutation[]).sort((a, b) => a.timestamp - b.timestamp));
			req.onerror = () => reject(req.error);
		});
	} catch { return []; }
}

async function queuePut(entry: QueuedMutation): Promise<void> {
	try {
		const db = await queueDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction('mutation-queue', 'readwrite');
			const req = tx.objectStore('mutation-queue').put(entry);
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch { /* non-fatal */ }
}

async function queueRemove(id: string): Promise<void> {
	try {
		const db = await queueDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction('mutation-queue', 'readwrite');
			const req = tx.objectStore('mutation-queue').delete(id);
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch { /* non-fatal */ }
}

async function queueRemoveByResource(resource: ResourceKind, resourceId: string | null): Promise<void> {
	try {
		const db = await queueDB();
		const all = await queueGetAll();
		const toDelete = all.filter(m => m.resource === resource && m.resourceId === resourceId);
		if (toDelete.length === 0) return;
		return new Promise((resolve, reject) => {
			const tx = db.transaction('mutation-queue', 'readwrite');
			const store = tx.objectStore('mutation-queue');
			let pending = toDelete.length;
			for (const m of toDelete) {
				const req = store.delete(m.id);
				req.onsuccess = () => { if (--pending === 0) resolve(); };
				req.onerror = () => reject(req.error);
			}
		});
	} catch { /* non-fatal */ }
}

// ── Reactive state ───────────────────────────────────────────────────────────

let _pendingCount = $state(0);
let _replaying = $state(false);
let _lastError = $state<string | null>(null);

export const syncState = {
	get pendingCount() { return _pendingCount; },
	get replaying() { return _replaying; },
	get lastError() { return _lastError; }
};

async function refreshCount() {
	const all = await queueGetAll();
	_pendingCount = all.length;
}

// ── Enqueue ──────────────────────────────────────────────────────────────────

export async function enqueue(
	resource: ResourceKind,
	resourceId: string | null,
	method: MutationMethod,
	url: string,
	body: unknown
): Promise<void> {
	// LWW dedup: DELETE supersedes all prior mutations for this resource+id.
	// PATCH/POST: drop older PATCH entries for same resource+id, keep latest.
	if (resourceId !== null) {
		if (method === 'DELETE') {
			// delete wins — remove all queued mutations for this resource
			await queueRemoveByResource(resource, resourceId);
		} else if (method === 'PATCH') {
			// collapse: remove older PATCHes, keep only this latest one
			const all = await queueGetAll();
			const older = all.filter(m => m.resource === resource && m.resourceId === resourceId && m.method === 'PATCH');
			for (const m of older) await queueRemove(m.id);
		}
	}

	const entry: QueuedMutation = {
		id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
		timestamp: Date.now(),
		resource,
		resourceId,
		method,
		url,
		body: body != null ? JSON.stringify(body) : null,
		retries: 0
	};
	await queuePut(entry);
	await refreshCount();
}

// ── Replay ───────────────────────────────────────────────────────────────────

const MAX_RETRIES = 3;
const BASE = import.meta.env.VITE_API_URL ?? '';

export async function replayQueue(): Promise<void> {
	if (_replaying) return;
	const queue = await queueGetAll();
	if (queue.length === 0) return;

	_replaying = true;
	_lastError = null;

	for (const mutation of queue) {
		try {
			const res = await fetch(`${BASE}${mutation.url}`, {
				method: mutation.method,
				headers: { 'Content-Type': 'application/json' },
				body: mutation.body ?? undefined
			});

			if (res.ok || res.status === 404) {
				// 404 on delete = already gone, treat as success
				await queueRemove(mutation.id);
				// Invalidate affected cache so next load gets fresh data
				await cacheClear(mutation.resource);
			} else if (res.status === 409 || res.status >= 400) {
				// Conflict or bad request — drop mutation, log error
				_lastError = `Sync conflict on ${mutation.resource} (${res.status}) — change discarded`;
				await queueRemove(mutation.id);
			} else {
				// Transient failure — increment retries
				if (mutation.retries >= MAX_RETRIES) {
					_lastError = `Failed to sync ${mutation.resource} after ${MAX_RETRIES} retries — change discarded`;
					await queueRemove(mutation.id);
				} else {
					await queuePut({ ...mutation, retries: mutation.retries + 1 });
				}
			}
		} catch {
			// Network error mid-replay — stop, try again next online event
			break;
		}
	}

	await refreshCount();
	_replaying = false;
}

// ── Online listener (singleton, module-level) ────────────────────────────────

let _listenerAttached = false;

export function initSync(): void {
	if (typeof window === 'undefined' || _listenerAttached) return;
	_listenerAttached = true;
	window.addEventListener('online', () => replayQueue());
	// Replay any queue left from previous session on startup if online
	if (navigator.onLine) replayQueue();
	refreshCount();
}
