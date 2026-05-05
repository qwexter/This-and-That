<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import Button from '$lib/ui/Button.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';

	const token = $derived($page.params.token!);

	let spaceTitle = $state<string | null>(null);
	let loading = $state(true);
	let joining = $state(false);
	let error = $state<string | null>(null);

	async function load() {
		try {
			const info = await api.getInvite(token);
			spaceTitle = info.spaceTitle;
		} catch {
			error = 'Invite link is invalid or has expired.';
		} finally {
			loading = false;
		}
	}

	async function join() {
		joining = true;
		try {
			await api.acceptInvite(token);
			goto('/');
		} catch {
			error = 'Failed to join. The invite may have expired or reached its limit.';
		} finally {
			joining = false;
		}
	}

	$effect(() => { load(); });
</script>

<div class="container">
	{#if loading}
		<EmptyState variant="page">Loading…</EmptyState>
	{:else if error}
		<EmptyState variant="error">{error}</EmptyState>
	{:else}
		<div class="card">
			<p class="label">You've been invited to join</p>
			<h1 class="title">{spaceTitle}</h1>
			<Button variant="primary" onclick={join} disabled={joining}>
				{joining ? 'Joining…' : 'Join space'}
			</Button>
		</div>
	{/if}
</div>

<style>
	.container {
		display: flex;
		align-items: center;
		justify-content: center;
		min-height: 60vh;
	}

	.card {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-4);
		padding: var(--space-8);
		background: var(--color-bg-card);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-card);
		max-width: 360px;
		width: 100%;
		text-align: center;
	}

	.label {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		margin: 0;
	}

	.title {
		font-size: 1.5rem;
		font-weight: 700;
		color: var(--color-text-primary);
		margin: 0;
	}
</style>
