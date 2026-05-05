import { toast } from './ui/toast.svelte';

export const POLL_INTERVAL_MS = 10_000;

type Status = 'connected' | 'connecting' | 'offline';

let _status = $state<Status>('connecting');
let _reconnectCallbacks: Array<() => void> = [];
let _pollTimer: ReturnType<typeof setTimeout> | null = null;
let _initialized = false;

export const connection = {
	get status() { return _status; },
	get isOffline() { return _status === 'offline'; },
	get isConnecting() { return _status === 'connecting'; },

	onReconnect(cb: () => void): () => void {
		_reconnectCallbacks.push(cb);
		return () => { _reconnectCallbacks = _reconnectCallbacks.filter(f => f !== cb); };
	}
};

const BASE = import.meta.env.VITE_API_URL ?? '';

async function probe(): Promise<boolean> {
	try {
		const res = await fetch(`${BASE}/health`, { cache: 'no-store' });
		return res.ok;
	} catch {
		return false;
	}
}

async function tick() {
	const wasOffline = _status === 'offline';
	const ok = await probe();

	if (ok) {
		if (wasOffline) {
			_status = 'connected';
			toast.success('Back online');
			for (const cb of _reconnectCallbacks) cb();
		} else {
			_status = 'connected';
		}
	} else {
		_status = 'offline';
	}

	_pollTimer = setTimeout(tick, POLL_INTERVAL_MS);
}

export function initConnection(): void {
	if (typeof window === 'undefined' || _initialized) return;
	_initialized = true;
	tick();
}
