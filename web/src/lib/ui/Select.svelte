<script lang="ts">
	/**
	 * Select — single-choice dropdown.
	 * Used for priority picker, space selector, existing-item picker.
	 *
	 * size: "sm" compact (space selector row), "md" form default.
	 */
	interface SelectOption {
		value: string;
		label: string;
	}

	interface Props {
		value?: string;
		options: SelectOption[];
		disabled?: boolean;
		size?: 'sm' | 'md';
		onchange?: (value: string) => void;
	}

	let {
		value = $bindable(''),
		options,
		disabled = false,
		size = 'md',
		onchange
	}: Props = $props();

	function handleChange(e: Event) {
		const v = (e.target as HTMLSelectElement).value;
		value = v;
		onchange?.(v);
	}
</script>

<select
	bind:value
	{disabled}
	class="select"
	data-size={size}
	onchange={handleChange}
>
	{#each options as opt (opt.value)}
		<option value={opt.value}>{opt.label}</option>
	{/each}
</select>

<style>
	.select {
		background: var(--color-bg-sunken);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		color: var(--color-text-primary);
		font-family: inherit;
		cursor: pointer;
		transition: border-color 0.1s;
		box-sizing: border-box;
	}

	.select:focus {
		outline: none;
		border-color: var(--color-accent);
	}

	.select:disabled { opacity: 0.5; cursor: default; }

	[data-size="sm"] { padding: 0.3rem 0.5rem;  font-size: var(--font-size-base); }
	[data-size="md"] { padding: 0.5rem 0.75rem; font-size: var(--font-size-md); width: 100%; }
</style>
