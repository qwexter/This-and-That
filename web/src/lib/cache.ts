const DB_NAME = 'tat-cache';
const DB_VERSION = 2;
const STORES = ['tasks', 'records', 'groups', 'spaces', 'feed'] as const;
type StoreName = typeof STORES[number];

let _db: IDBDatabase | null = null;

function openDB(): Promise<IDBDatabase> {
	if (_db) return Promise.resolve(_db);
	return new Promise((resolve, reject) => {
		const req = indexedDB.open(DB_NAME, DB_VERSION);
		req.onupgradeneeded = () => {
			for (const name of STORES) {
				if (!req.result.objectStoreNames.contains(name)) {
					req.result.createObjectStore(name);
				}
			}
			if (!req.result.objectStoreNames.contains('mutation-queue')) {
				const store = req.result.createObjectStore('mutation-queue', { keyPath: 'id' });
				store.createIndex('by-resource', ['resource', 'resourceId'], { unique: false });
			}
		};
		req.onsuccess = () => { _db = req.result; resolve(req.result); };
		req.onerror = () => reject(req.error);
	});
}

export async function cacheGet<T>(store: StoreName, key: string): Promise<T | undefined> {
	try {
		const db = await openDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction(store, 'readonly');
			const req = tx.objectStore(store).get(key);
			req.onsuccess = () => resolve(req.result as T | undefined);
			req.onerror = () => reject(req.error);
		});
	} catch {
		return undefined;
	}
}

export async function cachePut<T>(store: StoreName, key: string, value: T): Promise<void> {
	try {
		const db = await openDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction(store, 'readwrite');
			const req = tx.objectStore(store).put(value, key);
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch {
		// cache write failure is non-fatal
	}
}

export async function cacheDelete(store: StoreName, key: string): Promise<void> {
	try {
		const db = await openDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction(store, 'readwrite');
			const req = tx.objectStore(store).delete(key);
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch {
		// cache delete failure is non-fatal
	}
}

export async function cacheClear(store: StoreName): Promise<void> {
	try {
		const db = await openDB();
		return new Promise((resolve, reject) => {
			const tx = db.transaction(store, 'readwrite');
			const req = tx.objectStore(store).clear();
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch {
		// non-fatal
	}
}
