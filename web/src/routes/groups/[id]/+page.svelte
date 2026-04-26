<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Group, Task, Record, AddGroupItem } from '$lib/types';

	const id = $derived($page.params.id!);

	let group = $state<Group | null>(null);
	let tasks = $state<Task[]>([]);
	let records = $state<Record[]>([]);
	let unassignedTasks = $state<Task[]>([]);
	let unassignedRecords = $state<Record[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let editing = $state(false);
	let editTitle = $state('');
	let saving = $state(false);

	// add panel
	let addMode = $state<'none' | 'newTask' | 'newRecord' | 'existing'>('none');
	let addError = $state<string | null>(null);
	let adding = $state(false);

	// new task form
	let newTaskName = $state('');
	let newTaskDesc = $state('');

	// new record form
	let newRecordTitle = $state('');
	let newRecordContent = $state('');

	// existing picker
	let existingKind = $state<'task' | 'record'>('task');
	let selectedExistingId = $state('');

	async function load() {
		try {
			const [g, allTasks, allRecords] = await Promise.all([
				api.getGroup(id),
				api.getTasks(),
				api.getRecords()
			]);
			group = g;
			editTitle = g.title;
			tasks = allTasks.filter((t) => t.groupId === id);
			records = allRecords.filter((r) => r.groupId === id);
			unassignedTasks = allTasks.filter((t) => t.groupId === null);
			unassignedRecords = allRecords.filter((r) => r.groupId === null);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function saveTitle() {
		if (!editTitle.trim() || !group) return;
		saving = true;
		try {
			group = await api.updateGroup(id, { title: editTitle.trim() });
			editing = false;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function deleteGroup() {
		await api.deleteGroup(id);
		goto('/groups');
	}

	async function removeTask(taskId: string) {
		await api.updateTask(taskId, { clearGroup: true });
		tasks = tasks.filter((t) => t.id !== taskId);
	}

	async function removeRecord(recordId: string) {
		await api.updateRecord(recordId, { clearGroup: true });
		records = records.filter((r) => r.id !== recordId);
	}

	function cancelAdd() {
		addMode = 'none';
		addError = null;
		newTaskName = '';
		newTaskDesc = '';
		newRecordTitle = '';
		newRecordContent = '';
		selectedExistingId = '';
	}

	async function submitAdd() {
		addError = null;
		let items: AddGroupItem[] = [];

		if (addMode === 'newTask') {
			if (!newTaskName.trim()) { addError = 'Task name required'; return; }
			items = [{ kind: 'newTask', name: newTaskName.trim(), description: newTaskDesc.trim() || null }];
		} else if (addMode === 'newRecord') {
			if (!newRecordTitle.trim()) { addError = 'Record title required'; return; }
			items = [{ kind: 'newRecord', title: newRecordTitle.trim(), content: newRecordContent.trim() || null }];
		} else if (addMode === 'existing') {
			if (!selectedExistingId) { addError = 'Select an item'; return; }
			items = existingKind === 'task'
				? [{ kind: 'existingTask', id: selectedExistingId }]
				: [{ kind: 'existingRecord', id: selectedExistingId }];
		}

		adding = true;
		try {
			await api.addGroupItems(id, { items });
			cancelAdd();
			await load();
		} catch (e) {
			addError = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	$effect(() => { load(); });
</script>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if group}
	<div class="group-header">
		{#if editing}
			<input
				class="title-input"
				bind:value={editTitle}
				maxlength="200"
				onkeydown={(e) => e.key === 'Enter' && saveTitle()}
			/>
			<button onclick={saveTitle} disabled={saving || !editTitle.trim()}>Save</button>
			<button class="cancel" onclick={() => { editing = false; editTitle = group!.title; }}>Cancel</button>
		{:else}
			<h1 class="group-title">{group.title}</h1>
			<button class="edit-btn" onclick={() => { editing = true; }}>Edit</button>
			<button class="del-btn" onclick={deleteGroup}>Delete group</button>
		{/if}
	</div>

	<!-- Add items panel -->
	{#if addMode === 'none'}
		<div class="add-bar">
			<button class="add-btn" onclick={() => addMode = 'newTask'}>+ New task</button>
			<button class="add-btn" onclick={() => addMode = 'newRecord'}>+ New record</button>
			<button class="add-btn secondary" onclick={() => addMode = 'existing'}>+ Add existing</button>
		</div>
	{:else}
		<div class="add-panel">
			{#if addMode === 'newTask'}
				<h3>New task</h3>
				<input class="form-input" placeholder="Task name" bind:value={newTaskName} maxlength="200" />
				<textarea class="form-textarea" placeholder="Description (optional)" bind:value={newTaskDesc}></textarea>
			{:else if addMode === 'newRecord'}
				<h3>New record</h3>
				<input class="form-input" placeholder="Title" bind:value={newRecordTitle} maxlength="200" />
				<textarea class="form-textarea" placeholder="Content (optional)" bind:value={newRecordContent}></textarea>
			{:else if addMode === 'existing'}
				<h3>Add existing item</h3>
				<div class="kind-toggle">
					<button class:active={existingKind === 'task'} onclick={() => { existingKind = 'task'; selectedExistingId = ''; }}>Tasks</button>
					<button class:active={existingKind === 'record'} onclick={() => { existingKind = 'record'; selectedExistingId = ''; }}>Records</button>
				</div>
				{#if existingKind === 'task'}
					{#if unassignedTasks.length === 0}
						<p class="empty">No ungrouped tasks.</p>
					{:else}
						<select class="form-select" bind:value={selectedExistingId}>
							<option value="">Select a task…</option>
							{#each unassignedTasks as t (t.id)}
								<option value={t.id}>{t.name}</option>
							{/each}
						</select>
					{/if}
				{:else}
					{#if unassignedRecords.length === 0}
						<p class="empty">No ungrouped records.</p>
					{:else}
						<select class="form-select" bind:value={selectedExistingId}>
							<option value="">Select a record…</option>
							{#each unassignedRecords as r (r.id)}
								<option value={r.id}>{r.title}</option>
							{/each}
						</select>
					{/if}
				{/if}
			{/if}

			{#if addError}
				<p class="add-error">{addError}</p>
			{/if}

			<div class="panel-actions">
				<button class="submit-btn" onclick={submitAdd} disabled={adding}>
					{adding ? 'Saving…' : 'Add'}
				</button>
				<button class="cancel" onclick={cancelAdd} disabled={adding}>Cancel</button>
			</div>
		</div>
	{/if}

	<section class="section">
		<h2>Tasks ({tasks.length})</h2>
		{#if tasks.length === 0}
			<p class="empty">No tasks in this group.</p>
		{:else}
			<ul class="item-list">
				{#each tasks as task (task.id)}
					<li class="item" data-priority={task.priority.toLowerCase()}>
						<a href="/tasks/{task.id}" class="item-name" class:done={task.status === 'Done'}>{task.name}</a>
						<span class="badge">{task.priority}</span>
						<span class="status" class:done={task.status === 'Done'}>{task.status}</span>
						<button class="remove" onclick={() => removeTask(task.id)} title="Remove from group">↗</button>
					</li>
				{/each}
			</ul>
		{/if}
	</section>

	<section class="section">
		<h2>Records ({records.length})</h2>
		{#if records.length === 0}
			<p class="empty">No records in this group.</p>
		{:else}
			<ul class="item-list">
				{#each records as record (record.id)}
					<li class="item record-item">
						<a href="/records/{record.id}" class="item-name">{record.title}</a>
						{#if record.content}
							<span class="preview">{record.content.slice(0, 50)}{record.content.length > 50 ? '…' : ''}</span>
						{/if}
						<button class="remove" onclick={() => removeRecord(record.id)} title="Remove from group">↗</button>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}

<style>
	.state { text-align: center; color: #888; padding: 2rem 0; }
	.state.error { color: #f87171; }

	.group-header {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		margin-bottom: 1.5rem;
		flex-wrap: wrap;
	}

	.group-title {
		flex: 1;
		font-size: 1.3rem;
		font-weight: 700;
	}

	.title-input {
		flex: 1;
		padding: 0.4rem 0.6rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 1.1rem;
	}

	.group-header button {
		padding: 0.4rem 0.8rem;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.85rem;
	}

	.group-header button:not(.cancel):not(.del-btn) { background: #4f46e5; color: #fff; }
	.group-header button:disabled { opacity: 0.4; cursor: default; }
	.cancel { background: #2a2a4a !important; color: #aaa !important; }
	.edit-btn { background: #2a2a4a; color: #aaa; }
	.del-btn { background: #7f1d1d; color: #fca5a5; }

	/* add bar */
	.add-bar {
		display: flex;
		gap: 0.5rem;
		margin-bottom: 1.5rem;
		flex-wrap: wrap;
	}

	.add-btn {
		padding: 0.4rem 0.9rem;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		background: #16213e;
		color: #a5b4fc;
		cursor: pointer;
		font-size: 0.85rem;
	}

	.add-btn:hover { background: #1e2a50; }
	.add-btn.secondary { color: #9ca3af; }

	/* add panel */
	.add-panel {
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 8px;
		padding: 1rem;
		margin-bottom: 1.5rem;
		display: flex;
		flex-direction: column;
		gap: 0.6rem;
	}

	.add-panel h3 {
		font-size: 0.9rem;
		font-weight: 600;
		color: #a5b4fc;
		margin: 0;
	}

	.form-input, .form-select {
		padding: 0.45rem 0.6rem;
		background: #0f172a;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.9rem;
		width: 100%;
		box-sizing: border-box;
	}

	.form-textarea {
		padding: 0.45rem 0.6rem;
		background: #0f172a;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.9rem;
		width: 100%;
		box-sizing: border-box;
		min-height: 5rem;
		resize: vertical;
		font-family: inherit;
	}

	.kind-toggle {
		display: flex;
		gap: 0.4rem;
	}

	.kind-toggle button {
		padding: 0.3rem 0.8rem;
		border: 1px solid #2a2a4a;
		border-radius: 5px;
		background: transparent;
		color: #888;
		cursor: pointer;
		font-size: 0.8rem;
	}

	.kind-toggle button.active {
		background: #2a2a4a;
		color: #e2e2e2;
	}

	.add-error { color: #f87171; font-size: 0.82rem; }

	.panel-actions {
		display: flex;
		gap: 0.5rem;
	}

	.submit-btn {
		padding: 0.4rem 1rem;
		background: #4f46e5;
		color: #fff;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.85rem;
	}

	.submit-btn:disabled { opacity: 0.4; cursor: default; }

	.panel-actions .cancel {
		padding: 0.4rem 0.8rem;
		background: #2a2a4a;
		color: #aaa;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.85rem;
	}

	.section { margin-bottom: 2rem; }

	.section h2 {
		font-size: 0.85rem;
		color: #888;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		margin-bottom: 0.75rem;
	}

	.empty { color: #555; font-size: 0.85rem; }

	.item-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}

	.item {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.6rem 0.75rem;
		background: #16213e;
		border-radius: 6px;
		border-left: 3px solid transparent;
	}

	.item[data-priority='high'] { border-left-color: #f87171; }
	.item[data-priority='medium'] { border-left-color: #fbbf24; }
	.item[data-priority='low'] { border-left-color: #34d399; }
	.record-item { border-left-color: #38bdf8; }

	.item-name { flex: 1; font-size: 0.9rem; }
	.item-name.done { opacity: 0.4; text-decoration: line-through; }

	.badge, .status {
		font-size: 0.68rem;
		padding: 0.1rem 0.35rem;
		background: #2a2a4a;
		border-radius: 3px;
		color: #aaa;
	}

	.status { color: #34d399; }
	.status.done { color: #555; }

	.preview { font-size: 0.75rem; color: #666; flex: 1; }

	.remove {
		background: transparent;
		border: none;
		color: #555;
		cursor: pointer;
		font-size: 0.9rem;
		padding: 0 0.2rem;
	}

	.remove:hover { color: #fbbf24; }
</style>
