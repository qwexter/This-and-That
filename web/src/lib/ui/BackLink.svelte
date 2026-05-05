<script lang="ts">
	/**
	 * BackLink — navigation affordance at the top of detail pages.
	 * Signals "you came from somewhere, here's how to go back."
	 */
	interface Props {
		href: string;
		label?: string;
	}

	let { href, label = 'Back' }: Props = $props();
</script>

<a {href} class="back">← {label}</a>

<style>
	.back {
		display: inline-block;
		font-size: var(--font-size-base);
		color: var(--color-text-muted);
		margin-bottom: var(--space-6);
		transition: color 0.1s;
	}

	.back:hover { color: var(--color-text-primary); }
</style>
