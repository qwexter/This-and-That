<script lang="ts">
	/**
	 * Badge — compact label for entity kind, priority, status, or space membership.
	 *
	 * variant:
	 *   "kind-task" | "kind-record" | "kind-group" | "kind-space"
	 *     → uses --color-kind-* tokens; communicates what type of entity this is
	 *   "priority-high" | "priority-medium" | "priority-low"
	 *     → uses --color-priority-* tokens; communicates urgency
	 *   "status-todo" | "status-done"
	 *     → uses --color-status-* tokens; communicates task completion
	 *   "space-shared" | "space-private"
	 *     → uses --color-space-* tokens; communicates space membership
	 *   "neutral"
	 *     → uses --color-bg-overlay / --color-text-muted; generic label
	 */
	interface Props {
		variant?:
			| 'kind-task' | 'kind-record' | 'kind-group' | 'kind-space'
			| 'priority-high' | 'priority-medium' | 'priority-low'
			| 'status-todo' | 'status-done'
			| 'space-shared' | 'space-private'
			| 'neutral';
		pill?: boolean;
		children: import('svelte').Snippet;
	}

	let { variant = 'neutral', pill = false, children }: Props = $props();
</script>

<span class="badge" class:pill data-variant={variant}>
	{@render children()}
</span>

<style>
	.badge {
		display: inline-flex;
		align-items: center;
		font-size: var(--font-size-xs);
		font-weight: 600;
		letter-spacing: 0.03em;
		text-transform: uppercase;
		padding: 0.1rem 0.35rem;
		border-radius: var(--radius-sm);
		white-space: nowrap;
		line-height: 1.4;

		/* neutral default */
		background: var(--color-bg-overlay);
		color: var(--color-text-muted);
	}

	.badge.pill {
		border-radius: var(--radius-pill);
		padding: 0.15rem 0.5rem;
	}

	/* entity kinds */
	[data-variant="kind-task"]   { background: var(--color-kind-task-bg);   color: var(--color-kind-task-text); }
	[data-variant="kind-record"] { background: var(--color-kind-record-bg); color: var(--color-kind-record-text); }
	[data-variant="kind-group"]  { background: var(--color-kind-group-bg);  color: var(--color-kind-group-text); }
	[data-variant="kind-space"]  { background: var(--color-kind-space-bg);  color: var(--color-kind-space-text); }

	/* priority */
	[data-variant="priority-high"]   { background: var(--color-bg-overlay); color: var(--color-priority-high); }
	[data-variant="priority-medium"] { background: var(--color-bg-overlay); color: var(--color-priority-medium); }
	[data-variant="priority-low"]    { background: var(--color-bg-overlay); color: var(--color-priority-low); }

	/* status */
	[data-variant="status-todo"] { background: var(--color-bg-overlay); color: var(--color-status-todo); }
	[data-variant="status-done"] { background: var(--color-bg-overlay); color: var(--color-status-done); }

	/* space membership */
	[data-variant="space-shared"]  { background: var(--color-space-shared-bg);  color: var(--color-space-shared-text); }
	[data-variant="space-private"] { background: var(--color-space-private-bg); color: var(--color-space-private-text); }
</style>
