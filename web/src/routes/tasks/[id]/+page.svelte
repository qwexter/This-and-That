<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Task, TaskPriority, TaskStatus } from '$lib/types';

	const id = $derived($page.params.id!);

	let task = $state<Task | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let saving = $state(false);

	let editName = $state('');
	let editDescription = $state('');
	let editPriority = $state<TaskPriority>('Low');
	let editStatus = $state<TaskStatus>('Todo');
	let editDeadline = $state('');

	async function load() {
		try {
			task = await api.getTask(id);
			editName = task.name;
			editDescription = task.description ?? '';
			editPriority = task.priority;
			editStatus = task.status;
			editDeadline = task.deadline ?? '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function save() {
		if (!editName.trim()) return;
		saving = true;
		try {
			task = await api.updateTask(id, {
				name: editName.trim(),
				description: editDescription || null,
				priority: editPriority,
				status: editStatus,
				deadline: editDeadline || null
			});
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function remove() {
		await api.deleteTask(id);
		goto('/');
	}

	$effect(() => { load(); });
</script>

<a href="/" class="back">← Back</a>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if task}
	<form class="detail-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<div class="field">
			<label for="name">Name</label>
			<input id="name" bind:value={editName} required />
		</div>

		<div class="field">
			<label for="desc">Description</label>
			<textarea id="desc" bind:value={editDescription} rows="3"></textarea>
		</div>

		<div class="row">
			<div class="field">
				<label for="priority">Priority</label>
				<select id="priority" bind:value={editPriority}>
					<option value="Low">Low</option>
					<option value="Medium">Medium</option>
					<option value="High">High</option>
				</select>
			</div>

			<div class="field">
				<label for="status">Status</label>
				<select id="status" bind:value={editStatus}>
					<option value="Todo">Todo</option>
					<option value="Done">Done</option>
				</select>
			</div>
		</div>

		<div class="field">
			<label for="deadline">Deadline</label>
			<input id="deadline" type="datetime-local" bind:value={editDeadline} />
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
	.field textarea,
	.field select {
		padding: 0.5rem 0.75rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.95rem;
		font-family: inherit;
	}

	.field textarea { resize: vertical; }

	.row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1rem;
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
