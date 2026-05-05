<script lang="ts">
	/**
	 * Card — primary content container for feed items, list rows, and detail panels.
	 *
	 * accent: left border color signal. Maps to entity kind or priority.
	 *   "task-high" | "task-medium" | "task-low"  → priority-colored task card
	 *   "record"                                   → record kind color
	 *   "group"                                    → group kind color
	 *   "space"                                    → space kind color
	 *   "none" (default)                           → no accent border
	 *
	 * compact: tighter padding for list rows vs feed cards.
	 */
	import type { Snippet } from 'svelte';

	type CardAccent =
		| 'task-high' | 'task-medium' | 'task-low'
		| 'record' | 'group' | 'space'
		| 'none';

	interface Props {
		accent?: CardAccent;
		compact?: boolean;
		children: Snippet;
	}

	let { accent = 'none', compact = false, children }: Props = $props();
</script>

<div class="card" class:compact data-accent={accent}>
	{@render children()}
</div>

<style>
	.card {
		background: var(--color-bg-surface);
		border-radius: var(--radius-lg);
		border-top: 1px solid var(--color-card-border, transparent);
		border-right: 1px solid var(--color-card-border, transparent);
		border-bottom: 1px solid var(--color-card-border, transparent);
		border-left: 3px solid transparent;
		box-shadow: var(--shadow-card, none);
		padding: var(--space-3) var(--space-4);
	}

	.card.compact {
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
	}

	/* priority accents */
	[data-accent="task-high"]   { border-left-color: var(--color-priority-high); }
	[data-accent="task-medium"] { border-left-color: var(--color-priority-medium); }
	[data-accent="task-low"]    { border-left-color: var(--color-priority-low); }

	/* kind accents */
	[data-accent="record"] { border-left-color: var(--color-kind-record-border); }
	[data-accent="group"]  { border-left-color: var(--color-kind-group-border); }
	[data-accent="space"]  { border-left-color: var(--color-kind-space-border); }
</style>
