/**
 * Toast store — lightweight notification state.
 *
 * Variants:
 *   "success" — operation completed (saved, created, deleted)
 *   "error"   — operation failed
 *   "info"    — neutral message
 *
 * Usage:
 *   import { toast } from '$lib/ui/toast.svelte';
 *   toast.success('Saved');
 *   toast.error('Failed to save');
 */

export type ToastVariant = 'success' | 'error' | 'info';

export interface ToastItem {
	id: number;
	message: string;
	variant: ToastVariant;
}

let _items = $state<ToastItem[]>([]);
let _next = 0;

const DURATION = 3500;

function add(message: string, variant: ToastVariant) {
	const id = ++_next;
	_items = [..._items, { id, message, variant }];
	setTimeout(() => dismiss(id), DURATION);
}

function dismiss(id: number) {
	_items = _items.filter((t) => t.id !== id);
}

export const toast = {
	get items() { return _items; },
	success: (message: string) => add(message, 'success'),
	error:   (message: string) => add(message, 'error'),
	info:    (message: string) => add(message, 'info'),
	dismiss
};
