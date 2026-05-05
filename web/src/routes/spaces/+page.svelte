<script lang="ts">
	import { api } from '$lib/api';
	import type { Space } from '$lib/types';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	let spaces = $state<Space[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let adding = $state(false);

	async function load() {
		try {
			await api.getSpaces(
				(cached) => { spaces = cached; loading = false; },
				(fresh)  => { spaces = fresh;  loading = false; }
			);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function addSpace() {
		if (!newTitle.trim()) return;
		adding = true;
		try {
			const space = await api.createSpace({ title: newTitle.trim() });
			spaces = [...spaces, space];
			newTitle = '';
			toast.success('Space created');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteSpace(id);
		spaces = spaces.filter((s) => s.id !== id);
		toast.success('Space deleted');
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<div class="add-form">
	<TextInput bind:value={newTitle} placeholder="New space…" maxlength={200}
		onkeydown={(e) => e.key === 'Enter' && addSpace()} />
	<Button variant="primary" onclick={addSpace} disabled={adding || !newTitle.trim()}>Add</Button>
</div>

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if spaces.length === 0}
	<EmptyState variant="page">No spaces yet.</EmptyState>
{:else}
	<ul class="list">
		{#each spaces as space (space.id)}
			<li>
				<Card accent="space" compact>
					<div class="row">
						<a href="/spaces/{space.id}" class="title">{space.title}</a>
						{#if space.isPrivate}
							<Badge variant="space-private" pill>Private</Badge>
						{:else}
							<Button variant="icon" onclick={() => remove(space.id)} aria-label="Delete">×</Button>
						{/if}
					</div>
				</Card>
			</li>
		{/each}
	</ul>
{/if}

<style>
	.add-form {
		display: flex;
		gap: var(--space-2);
		margin-bottom: var(--space-6);
		align-items: center;
	}

	.add-form :global(.input) { flex: 1; }

	.list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.row {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.title {
		flex: 1;
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
	}
</style>
