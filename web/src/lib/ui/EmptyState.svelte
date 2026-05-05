<script lang="ts">
	/**
	 * EmptyState — placeholder shown when a list has no items.
	 *
	 * variant:
	 *   "page"    → centered, larger, for full-page empty screens
	 *   "inline"  → left-aligned, small, for section-level empty lists
	 *   "error"   → error color, for failed loads
	 */
	import type { Snippet } from 'svelte';

	interface Props {
		variant?: 'page' | 'inline' | 'error';
		children: Snippet;
	}

	let { variant = 'inline', children }: Props = $props();
</script>

<p class="empty" data-variant={variant}>
	{@render children()}
</p>

<style>
	.empty {
		color: var(--color-text-muted);
		font-size: var(--font-size-base);
	}

	[data-variant="page"] {
		text-align: center;
		padding: var(--space-8) 0;
		font-size: var(--font-size-md);
		color: var(--color-text-muted);
	}

	[data-variant="inline"] {
		color: var(--color-text-faint);
		font-size: var(--font-size-base);
	}

	[data-variant="error"] {
		color: var(--color-error);
		text-align: center;
		padding: var(--space-8) 0;
	}
</style>
