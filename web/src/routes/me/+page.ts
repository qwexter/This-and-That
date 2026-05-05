import { features } from '$lib/features';
import { error } from '@sveltejs/kit';

export function load() {
	if (!features.profile) throw error(404, 'Not found');
}
