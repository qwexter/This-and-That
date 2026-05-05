<script lang="ts">
	import { connection } from '$lib/connection.svelte';
	import { syncState } from '$lib/sync.svelte';

	const status = $derived(connection.status);
	const pendingCount = $derived(syncState.pendingCount);
	const replaying = $derived(syncState.replaying);
	const lastError = $derived(syncState.lastError);

	const visible = $derived(status !== 'connected' || replaying || !!lastError);

	function label(): string {
		if (replaying) return `Syncing ${pendingCount} change${pendingCount === 1 ? '' : 's'}…`;
		if (lastError) return lastError;
		if (status === 'connecting') return pendingCount > 0 ? `Connecting — ${pendingCount} change${pendingCount === 1 ? '' : 's'} queued` : 'Connecting…';
		if (status === 'offline') return pendingCount > 0 ? `Offline — ${pendingCount} change${pendingCount === 1 ? '' : 's'} queued` : 'Offline — cached data shown';
		return '';
	}

	function barClass(): string {
		if (lastError) return 'bar error';
		if (replaying) return 'bar syncing';
		if (status === 'connecting') return 'bar connecting';
		return 'bar offline';
	}
</script>

{#if visible}
	<div class={barClass()} role="status" aria-live="polite">
		{#if status === 'connecting' && !replaying && !lastError}
			<span class="dot pulse"></span>
		{/if}
		<span>{label()}</span>
	</div>
{/if}

<style>
	.bar {
		width: 100%;
		padding: 0.3rem var(--space-4);
		font-size: var(--font-size-xs);
		font-weight: 500;
		text-align: center;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		transition: background 0.2s;
	}

	.connecting {
		background: var(--color-bg-elevated);
		color: var(--color-text-muted);
	}

	.offline {
		background: #7f1d1d22;
		color: var(--color-priority-high);
	}

	.syncing {
		background: var(--color-accent);
		color: #fff;
	}

	.error {
		background: var(--color-priority-high);
		color: #fff;
	}

	.dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: currentColor;
		flex-shrink: 0;
	}

	.pulse {
		animation: pulse 1.4s ease-in-out infinite;
	}

	@keyframes pulse {
		0%, 100% { opacity: 1; }
		50%       { opacity: 0.25; }
	}
</style>
