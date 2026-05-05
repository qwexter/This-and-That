<script lang="ts">
	/**
	 * Toaster — renders active toasts from the toast store.
	 * Mount once in +layout.svelte, outside main content flow.
	 * Fixed top-right, stacks downward, auto-dismisses after 3.5s.
	 */
	import { toast } from './toast.svelte';
</script>

{#if toast.items.length > 0}
	<div class="toaster" role="log" aria-live="polite" aria-label="Notifications">
		{#each toast.items as item (item.id)}
			<div class="toast" data-variant={item.variant}>
				<span class="message">{item.message}</span>
				<button class="close" onclick={() => toast.dismiss(item.id)} aria-label="Dismiss">×</button>
			</div>
		{/each}
	</div>
{/if}

<style>
	.toaster {
		position: fixed;
		top: 1rem;
		right: 1rem;
		z-index: 100;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		max-width: min(360px, calc(100vw - 2rem));
		pointer-events: none;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-lg);
		font-size: var(--font-size-base);
		font-family: inherit;
		pointer-events: all;
		animation: slide-in 0.18s ease-out;
		box-shadow: 0 4px 16px rgba(0,0,0,0.25);

		/* default / info */
		background: var(--color-bg-elevated);
		color: var(--color-text-primary);
		border-left: 3px solid var(--color-border);
	}

	[data-variant="success"] {
		border-left-color: var(--color-priority-low);
	}

	[data-variant="error"] {
		border-left-color: var(--color-error);
		color: var(--color-error);
	}

	[data-variant="info"] {
		border-left-color: var(--color-accent);
	}

	.message { flex: 1; line-height: 1.4; }

	.close {
		background: transparent;
		border: none;
		color: var(--color-text-faint);
		cursor: pointer;
		font-size: 1.1rem;
		line-height: 1;
		padding: 0;
		flex-shrink: 0;
	}

	.close:hover { color: var(--color-text-primary); }

	@keyframes slide-in {
		from { transform: translateX(110%); opacity: 0; }
		to   { transform: translateX(0);    opacity: 1; }
	}
</style>
