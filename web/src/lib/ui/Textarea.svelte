<script lang="ts">
	/**
	 * Textarea — multi-line text entry.
	 * Used for task descriptions and record content.
	 * Shows optional character counter when maxlength is provided.
	 */
	interface Props {
		value?: string;
		placeholder?: string;
		maxlength?: number;
		disabled?: boolean;
		rows?: number;
		showCounter?: boolean;
	}

	let {
		value = $bindable(''),
		placeholder = '',
		maxlength,
		disabled = false,
		rows = 4,
		showCounter = false
	}: Props = $props();

	const remaining = $derived(maxlength != null ? maxlength - value.length : null);
</script>

<div class="wrap">
	<textarea
		bind:value
		{placeholder}
		{maxlength}
		{disabled}
		{rows}
		class="textarea"
	></textarea>
	{#if showCounter && remaining != null}
		<span class="counter" class:warn={remaining < 50}>{remaining}</span>
	{/if}
</div>

<style>
	.wrap { position: relative; width: 100%; }

	.textarea {
		width: 100%;
		background: var(--color-bg-sunken);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		color: var(--color-text-primary);
		font-family: inherit;
		font-size: var(--font-size-base);
		padding: 0.5rem 0.75rem;
		box-sizing: border-box;
		resize: vertical;
		min-height: 5rem;
		line-height: 1.5;
		transition: border-color 0.1s;
	}

	.textarea::placeholder { color: var(--color-text-faint); }

	.textarea:focus {
		outline: none;
		border-color: var(--color-accent);
	}

	.textarea:disabled { opacity: 0.5; cursor: default; }

	.counter {
		position: absolute;
		bottom: 0.4rem;
		right: 0.5rem;
		font-size: var(--font-size-xs);
		color: var(--color-text-faint);
		pointer-events: none;
	}

	.counter.warn { color: var(--color-priority-high); }
</style>
