<script lang="ts">
	import { api } from '$lib/api';
	import type { Record, AddRecord } from '$lib/types';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	let titleInput: ReturnType<typeof TextInput> | undefined;
	let records = $state<Record[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let adding = $state(false);

	async function load() {
		try {
			await api.getRecords(
				(cached) => { records = cached; loading = false; },
				(fresh)  => { records = fresh;  loading = false; }
			);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function addRecord() {
		if (!newTitle.trim()) return;
		adding = true;
		try {
			const body: AddRecord = { title: newTitle.trim() };
			const record = await api.createRecord(body);
			records = [...records, record];
			newTitle = '';
			toast.success('Record created');
			titleInput?.focus();
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteRecord(id);
		records = records.filter((r) => r.id !== id);
		toast.success('Record deleted');
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<div class="add-form">
	<TextInput bind:this={titleInput} bind:value={newTitle} placeholder="New record…" onkeydown={(e) => e.key === 'Enter' && addRecord()} />
	<Button variant="primary" onclick={addRecord} disabled={adding || !newTitle.trim()}>Add</Button>
</div>

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if records.length === 0}
	<EmptyState variant="page">No records yet.</EmptyState>
{:else}
	<ul class="list">
		{#each records as record (record.id)}
			<li>
				<Card accent="record" compact>
					<div class="row">
						<a href="/records/{record.id}" class="title">{record.title}</a>
						{#if record.content}
							<span class="preview">{record.content.slice(0, 60)}{record.content.length > 60 ? '…' : ''}</span>
						{/if}
						<Button variant="icon" onclick={() => remove(record.id)} aria-label="Delete">×</Button>
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
		align-items: baseline;
		gap: var(--space-3);
	}

	.title {
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
		flex-shrink: 0;
	}

	.preview {
		flex: 1;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		overflow: hidden;
		white-space: nowrap;
		text-overflow: ellipsis;
	}
</style>
