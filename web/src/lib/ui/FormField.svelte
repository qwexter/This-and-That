<script lang="ts">
	/**
	 * FormField — label + input slot wrapper.
	 * Enforces consistent label styling and vertical rhythm across all detail forms.
	 */
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		id?: string;
		children: Snippet;
	}

	let { label, id, children }: Props = $props();
</script>

<div class="field">
	<label for={id} class="label">{label}</label>
	{@render children()}
</div>

<style>
	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.label {
		font-size: var(--font-size-xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}
</style>
