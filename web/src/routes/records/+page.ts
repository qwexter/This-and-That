import { features } from '$lib/features';
import { error } from '@sveltejs/kit';

export function load() {
	if (!features.records) throw error(404, 'Not found');
}
