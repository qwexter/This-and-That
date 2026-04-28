<script lang="ts">
	/**
	 * Button — all interactive triggers in the app.
	 *
	 * variant:
	 *   "primary"   → accent fill; main CTA (Save, Add, Submit)
	 *   "secondary" → overlay fill; supporting actions (Edit, Cancel, Load more)
	 *   "ghost"     → transparent with border; tertiary / toggle buttons
	 *   "danger"    → danger fill; destructive actions (Delete)
	 *   "icon"      → no padding, transparent; inline icon triggers (×, ↗, ✓ circle)
	 *
	 * size:
	 *   "sm" | "md" (default) | "lg"
	 */
	import type { Snippet } from 'svelte';

	interface Props {
		variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'icon';
		size?: 'sm' | 'md' | 'lg';
		type?: 'button' | 'submit' | 'reset';
		disabled?: boolean;
		active?: boolean;
		onclick?: (e: MouseEvent) => void;
		children: Snippet;
	}

	let {
		variant = 'secondary',
		size = 'md',
		type = 'button',
		disabled = false,
		active = false,
		onclick,
		children
	}: Props = $props();
</script>

<button
	{type}
	{disabled}
	class="btn"
	data-variant={variant}
	data-size={size}
	class:active
	{onclick}
>
	{@render children()}
</button>

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		border: none;
		border-radius: var(--radius-md);
		cursor: pointer;
		font-family: inherit;
		font-weight: 500;
		line-height: 1;
		transition: background 0.1s, color 0.1s, opacity 0.1s;
	}

	.btn:disabled {
		opacity: 0.4;
		cursor: default;
		pointer-events: none;
	}

	/* sizes */
	[data-size="sm"] { padding: 0.25rem 0.6rem;  font-size: var(--font-size-xs); }
	[data-size="md"] { padding: 0.45rem 0.9rem;  font-size: var(--font-size-base); }
	[data-size="lg"] { padding: 0.6rem  1.25rem; font-size: var(--font-size-md); }

	/* primary — accent fill */
	[data-variant="primary"] {
		background: var(--color-accent);
		color: #fff;
	}
	[data-variant="primary"]:hover:not(:disabled) {
		background: var(--color-accent-hover);
	}

	/* secondary — surface overlay */
	[data-variant="secondary"] {
		background: var(--color-bg-overlay);
		color: var(--color-text-muted);
	}
	[data-variant="secondary"]:hover:not(:disabled) {
		background: var(--color-bg-elevated);
		color: var(--color-text-primary);
	}
	[data-variant="secondary"].active {
		background: var(--color-bg-elevated);
		color: var(--color-text-primary);
	}

	/* ghost — border only */
	[data-variant="ghost"] {
		background: transparent;
		border: 1px solid var(--color-border);
		color: var(--color-accent-text);
	}
	[data-variant="ghost"]:hover:not(:disabled) {
		background: var(--color-bg-elevated);
	}
	[data-variant="ghost"].active {
		background: var(--color-bg-overlay);
		color: var(--color-text-primary);
	}

	/* danger — destructive */
	[data-variant="danger"] {
		background: var(--color-danger);
		color: var(--color-danger-text);
	}
	[data-variant="danger"]:hover:not(:disabled) {
		filter: brightness(1.1);
	}

	/* icon — transparent, minimal footprint */
	[data-variant="icon"] {
		background: transparent;
		color: var(--color-text-faint);
		padding: 0.1rem 0.25rem;
		font-size: var(--font-size-md);
		line-height: 1;
	}
	[data-variant="icon"]:hover:not(:disabled) {
		color: var(--color-text-primary);
	}
</style>
