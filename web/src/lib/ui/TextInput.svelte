<script lang="ts">
	/**
	 * TextInput — single-line text entry.
	 *
	 * Covers: task name, record title, group title, space title, member ID,
	 * inline rename fields, quick-add bars.
	 *
	 * size: "sm" for compact inline edits, "md" (default) for forms.
	 */
	interface Props {
		id?: string;
		value?: string;
		placeholder?: string;
		maxlength?: number;
		disabled?: boolean;
		size?: 'sm' | 'md';
		type?: 'text' | 'search' | 'datetime-local';
		autofocus?: boolean;
		onkeydown?: (e: KeyboardEvent) => void;
		oninput?: (e: Event) => void;
	}

	let {
		id,
		value = $bindable(''),
		placeholder = '',
		maxlength,
		disabled = false,
		size = 'md',
		type = 'text',
		autofocus = false,
		onkeydown,
		oninput
	}: Props = $props();

	let el = $state<HTMLInputElement | null>(null);

	export function focus() { el?.focus(); }
	export function select() { el?.select(); }
</script>

<!-- svelte-ignore a11y_autofocus -->
<input
	{id}
	bind:this={el}
	{type}
	bind:value
	{placeholder}
	{maxlength}
	{disabled}
	{autofocus}
	{onkeydown}
	{oninput}
	class="input"
	data-size={size}
/>

<style>
	.input {
		width: 100%;
		background: var(--color-bg-sunken);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		color: var(--color-text-primary);
		font-family: inherit;
		box-sizing: border-box;
		transition: border-color 0.1s;
	}

	.input::placeholder { color: var(--color-text-faint); }

	.input:focus {
		outline: none;
		border-color: var(--color-accent);
	}

	.input:disabled { opacity: 0.5; cursor: default; }

	[data-size="sm"] { padding: 0.3rem 0.5rem;  font-size: var(--font-size-base); }
	[data-size="md"] { padding: 0.5rem 0.75rem; font-size: var(--font-size-md); }
</style>
