<script lang="ts">
	/**
	 * SectionHeading — section label used above lists within a page.
	 * Uppercase, muted, small — signals structure without visual weight.
	 */
	import type { Snippet } from 'svelte';

	interface Props {
		children: Snippet;
	}

	let { children }: Props = $props();
</script>

<h2 class="heading">
	{@render children()}
</h2>

<style>
	.heading {
		font-size: var(--font-size-xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
		margin-bottom: var(--space-3);
	}
</style>
