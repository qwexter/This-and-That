<script lang="ts">
	import { api } from '$lib/api';
	import type { Record, AddRecord } from '$lib/types';

	let records = $state<Record[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let newTitle = $state('');
	let adding = $state(false);

	async function load() {
		try {
			records = await api.getRecords();
		} catch (e) {
			error = (e as Error).message;
		} finally {
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
		} catch (e) {
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteRecord(id);
		records = records.filter((r) => r.id !== id);
	}

	$effect(() => { load(); });
</script>

<section class="add-form">
	<input
		bind:value={newTitle}
		placeholder="New record…"
		onkeydown={(e) => e.key === 'Enter' && addRecord()}
	/>
	<button onclick={addRecord} disabled={adding || !newTitle.trim()}>Add</button>
</section>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if records.length === 0}
	<p class="state">No records yet.</p>
{:else}
	<ul class="record-list">
		{#each records as record (record.id)}
			<li class="record">
				<a href="/records/{record.id}" class="record-title">{record.title}</a>
				{#if record.content}
					<span class="preview">{record.content.slice(0, 60)}{record.content.length > 60 ? '…' : ''}</span>
				{/if}
				<button class="del" onclick={() => remove(record.id)} aria-label="Delete">×</button>
			</li>
		{/each}
	</ul>
{/if}

<style>
	.add-form {
		display: flex;
		gap: 0.5rem;
		margin-bottom: 1.5rem;
	}

	.add-form input {
		flex: 1;
		padding: 0.5rem 0.75rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 1rem;
	}

	.add-form button {
		padding: 0.5rem 1rem;
		background: #4f46e5;
		border: none;
		border-radius: 6px;
		color: #fff;
		cursor: pointer;
		font-size: 0.9rem;
	}

	.add-form button:disabled {
		opacity: 0.4;
		cursor: default;
	}

	.state {
		text-align: center;
		color: #888;
		padding: 2rem 0;
	}

	.state.error { color: #f87171; }

	.record-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.record {
		display: flex;
		align-items: baseline;
		gap: 0.75rem;
		padding: 0.75rem;
		background: #16213e;
		border-radius: 8px;
		border-left: 3px solid #4f46e5;
	}

	.record-title {
		font-size: 0.95rem;
		font-weight: 500;
		flex-shrink: 0;
	}

	.preview {
		flex: 1;
		font-size: 0.8rem;
		color: #888;
		overflow: hidden;
		white-space: nowrap;
		text-overflow: ellipsis;
	}

	.del {
		background: transparent;
		border: none;
		color: #888;
		cursor: pointer;
		font-size: 1.2rem;
		line-height: 1;
		padding: 0 0.25rem;
		flex-shrink: 0;
	}

	.del:hover { color: #f87171; }
</style>
