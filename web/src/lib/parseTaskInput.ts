import type { TaskPriority } from './types';

export interface ParsedTaskInput {
	name: string;
	priority: TaskPriority | null;
	groupName: string | null;
}

const PRIORITY_MAP: Record<string, TaskPriority> = {
	'!high': 'High',   '!!!': 'High',
	'!medium': 'Medium', '!!': 'Medium',
	'!low': 'Low',     '!': 'Low',
};

export function parseTaskInput(raw: string, groupTitles: string[]): ParsedTaskInput {
	let priority: TaskPriority | null = null;
	let groupName: string | null = null;
	const tokens = raw.trim().split(/\s+/);
	const nameTokens: string[] = [];

	for (const token of tokens) {
		const lower = token.toLowerCase();

		// priority: !high !medium !low !!! !! !
		if (lower in PRIORITY_MAP) {
			priority = PRIORITY_MAP[lower];
			continue;
		}

		// group: #prefix match against known groups
		if (token.startsWith('#') && token.length > 1) {
			const query = token.slice(1).toLowerCase();
			const match = groupTitles.find(t => t.toLowerCase().startsWith(query));
			if (match) {
				groupName = match;
				continue;
			}
		}

		nameTokens.push(token);
	}

	return { name: nameTokens.join(' ').trim(), priority, groupName };
}
