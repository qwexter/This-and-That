<script lang="ts">
	/**
	 * CheckCircle — task completion toggle button.
	 *
	 * Communicates task status visually with an interactive circle.
	 * Intentionally distinct from Button to keep task-domain semantics clear
	 * and allow future animation or icon replacement without touching Button.
	 */
	interface Props {
		checked?: boolean;
		label?: string;
		onclick?: () => void;
	}

	let { checked = false, label = checked ? 'Mark todo' : 'Mark done', onclick }: Props = $props();
</script>

<button
	type="button"
	class="check"
	class:checked
	aria-label={label}
	{onclick}
></button>

<style>
	.check {
		width: 20px;
		height: 20px;
		border-radius: var(--radius-pill);
		border: 2px solid var(--color-accent);
		background: transparent;
		cursor: pointer;
		flex-shrink: 0;
		transition: background 0.1s;
	}

	.check.checked {
		background: var(--color-accent);
	}

	.check:hover:not(.checked) {
		background: var(--color-accent-subtle);
	}
</style>
