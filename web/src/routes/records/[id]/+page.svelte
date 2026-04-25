<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Record } from '$lib/types';

	const id = $derived($page.params.id!);

	let record = $state<Record | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let saving = $state(false);

	let editTitle = $state('');
	let editContent = $state('');

	async function load() {
		try {
			record = await api.getRecord(id);
			editTitle = record.title;
			editContent = record.content ?? '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function save() {
		if (!editTitle.trim()) return;
		saving = true;
		try {
			record = await api.updateRecord(id, {
				title: editTitle.trim(),
				content: editContent || null
			});
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function remove() {
		await api.deleteRecord(id);
		goto('/records');
	}

	$effect(() => { load(); });
</script>

<a href="/records" class="back">← Records</a>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if record}
	<form class="detail-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<div class="field">
			<label for="title">Title</label>
			<input id="title" bind:value={editTitle} required maxlength="200" />
		</div>

		<div class="field">
			<label for="content">Content</label>
			<textarea id="content" bind:value={editContent} rows="10" maxlength="5000"></textarea>
			<span class="char-count">{editContent.length} / 5000</span>
		</div>

		<div class="actions">
			<button type="submit" disabled={saving}>Save</button>
			<button type="button" class="danger" onclick={remove}>Delete</button>
		</div>
	</form>
{/if}

<style>
	.back {
		display: inline-block;
		color: #888;
		margin-bottom: 1.5rem;
		font-size: 0.9rem;
	}

	.back:hover { color: #e2e2e2; }

	.state {
		text-align: center;
		color: #888;
		padding: 2rem 0;
	}

	.state.error { color: #f87171; }

	.detail-form {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
	}

	.field label {
		font-size: 0.8rem;
		color: #888;
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.field input,
	.field textarea {
		padding: 0.5rem 0.75rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.95rem;
		font-family: inherit;
	}

	.field textarea { resize: vertical; }

	.char-count {
		font-size: 0.75rem;
		color: #888;
		text-align: right;
	}

	.actions {
		display: flex;
		gap: 0.75rem;
		margin-top: 0.5rem;
	}

	.actions button {
		padding: 0.5rem 1.25rem;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.9rem;
	}

	.actions button[type='submit'] {
		background: #4f46e5;
		color: #fff;
	}

	.actions button[type='submit']:disabled {
		opacity: 0.4;
		cursor: default;
	}

	.actions .danger {
		background: transparent;
		border: 1px solid #f87171;
		color: #f87171;
	}

	.actions .danger:hover {
		background: #f87171;
		color: #fff;
	}
</style>
