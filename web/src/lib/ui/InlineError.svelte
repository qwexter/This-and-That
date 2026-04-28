<script lang="ts">
	/**
	 * InlineError — small error message inside a form or panel.
	 * Distinct from EmptyState "error" which is page-level.
	 */
	import type { Snippet } from 'svelte';

	interface Props {
		children: Snippet;
	}

	let { children }: Props = $props();
</script>

<p class="err">{@render children()}</p>

<style>
	.err {
		color: var(--color-error);
		font-size: var(--font-size-sm);
		margin: 0;
	}
</style>
