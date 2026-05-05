<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Group, Space, Task, Record, AddGroupItem } from '$lib/types';
	import BackLink from '$lib/ui/BackLink.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import InlineError from '$lib/ui/InlineError.svelte';
	import SectionHeading from '$lib/ui/SectionHeading.svelte';
	import Select from '$lib/ui/Select.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	const id = $derived($page.params.id!);

	let group = $state<Group | null>(null);
	let spaces = $state<Space[]>([]);
	let tasks = $state<Task[]>([]);
	let records = $state<Record[]>([]);
	let unassignedTasks = $state<Task[]>([]);
	let unassignedRecords = $state<Record[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let editing = $state(false);
	let editTitle = $state('');
	let saving = $state(false);
	let movingSpace = $state(false);

	const spaceMap = $derived(new Map(spaces.map((s) => [s.id, s])));
	const nonPrivateSpaces = $derived(spaces.filter((s) => !s.isPrivate));

	const spaceOptions = $derived([
		{ value: '', label: 'Private' },
		...nonPrivateSpaces.map(s => ({ value: s.id, label: s.title }))
	]);

	function currentSpaceName(): string {
		if (!group?.spaceId) return 'Private';
		return spaceMap.get(group.spaceId)?.title ?? 'Private';
	}

	function isCurrentSpacePrivate(): boolean {
		if (!group?.spaceId) return true;
		return spaceMap.get(group.spaceId)?.isPrivate ?? true;
	}

	async function moveToSpace(targetSpaceId: string) {
		if (!group) return;
		movingSpace = true;
		try {
			const updated = targetSpaceId === ''
				? await api.updateGroup(id, { clearSpace: true })
				: await api.updateGroup(id, { spaceId: targetSpaceId });
			if (updated) group = updated;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			movingSpace = false;
		}
	}

	// add panel
	type AddMode = 'none' | 'newTask' | 'newRecord' | 'existing';
	let addMode = $state<AddMode>('none');
	let addError = $state<string | null>(null);
	let adding = $state(false);

	let newTaskName = $state('');
	let newTaskDesc = $state('');
	let newRecordTitle = $state('');
	let newRecordContent = $state('');
	let existingKind = $state<'task' | 'record'>('task');
	let selectedExistingId = $state('');

	const existingTaskOptions = $derived([
		{ value: '', label: 'Select a task…' },
		...unassignedTasks.map(t => ({ value: t.id, label: t.name }))
	]);

	const existingRecordOptions = $derived([
		{ value: '', label: 'Select a record…' },
		...unassignedRecords.map(r => ({ value: r.id, label: r.title }))
	]);

	function applyData(g: NonNullable<typeof group>, allTasks: typeof tasks, allRecords: typeof records, allSpaces: typeof spaces) {
		group = g;
		spaces = allSpaces;
		editTitle = g.title;
		tasks = allTasks.filter((t) => t.groupId === id);
		records = allRecords.filter((r) => r.groupId === id);
		unassignedTasks = allTasks.filter((t) => t.groupId === null);
		unassignedRecords = allRecords.filter((r) => r.groupId === null);
		loading = false;
	}

	async function load() {
		try {
			// Collect latest value per slot from cache+network; render as soon as all 4 slots filled.
			let g: NonNullable<typeof group> | null = null;
			let allTasks: typeof tasks = [];
			let allRecords: typeof records = [];
			let allSpaces: typeof spaces = [];
			let filled = 0;
			const slots = [false, false, false, false];
			function fill(i: number) { if (!slots[i]) { slots[i] = true; filled++; } }
			function tryRender() { if (filled === 4 && g) applyData(g, allTasks, allRecords, allSpaces); }

			await Promise.all([
				api.getGroup(id,
					(c) => { g = c;          fill(0); tryRender(); },
					(f) => { g = f;          fill(0); tryRender(); }
				),
				api.getTasks(
					(c) => { allTasks = c;   fill(1); tryRender(); },
					(f) => { allTasks = f;   fill(1); tryRender(); }
				),
				api.getRecords(
					(c) => { allRecords = c; fill(2); tryRender(); },
					(f) => { allRecords = f; fill(2); tryRender(); }
				),
				api.getSpaces(
					(c) => { allSpaces = c;  fill(3); tryRender(); },
					(f) => { allSpaces = f;  fill(3); tryRender(); }
				)
			]);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function saveTitle() {
		if (!editTitle.trim() || !group) return;
		saving = true;
		try {
			const renamed = await api.updateGroup(id, { title: editTitle.trim() });
			if (renamed) group = renamed;
			editing = false;
			toast.success('Group renamed');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function deleteGroup() {
		await api.deleteGroup(id);
		toast.success('Group deleted');
		goto('/groups');
	}

	async function removeTask(taskId: string) {
		await api.updateTask(taskId, { clearGroup: true });
		tasks = tasks.filter((t) => t.id !== taskId);
		toast.info('Task removed from group');
	}

	async function removeRecord(recordId: string) {
		await api.updateRecord(recordId, { clearGroup: true });
		records = records.filter((r) => r.id !== recordId);
		toast.info('Record removed from group');
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
			toast.success('Item added to group');
			await load();
		} catch (e) {
			addError = (e as Error).message;
			toast.error((e as Error).message);
		} finally {
			adding = false;
		}
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<BackLink href="/groups" label="Groups" />

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if group}

	<!-- Header -->
	<div class="header">
		{#if editing}
			<TextInput bind:value={editTitle} maxlength={200} size="sm" autofocus
				onkeydown={(e) => {
					if (e.key === 'Enter') saveTitle();
					if (e.key === 'Escape') { editing = false; editTitle = group!.title; }
				}} />
			<Button variant="primary" size="sm" onclick={saveTitle} disabled={saving || !editTitle.trim()}>Save</Button>
			<Button variant="secondary" size="sm" onclick={() => { editing = false; editTitle = group!.title; }}>Cancel</Button>
		{:else}
			<h1 class="title">{group.title}</h1>
			<Badge variant={isCurrentSpacePrivate() ? 'space-private' : 'space-shared'} pill>
				{currentSpaceName()}
			</Badge>
			<Button variant="secondary" size="sm" onclick={() => editing = true}>Edit</Button>
			<Button variant="danger" size="sm" onclick={deleteGroup}>Delete</Button>
		{/if}
	</div>

	<!-- Space selector -->
	<div class="space-row">
		<span class="space-label">Space</span>
		<Select
			value={group.spaceId ?? ''}
			options={spaceOptions}
			size="sm"
			disabled={movingSpace}
			onchange={moveToSpace}
		/>
	</div>

	<!-- Add items panel -->
	{#if addMode === 'none'}
		<div class="add-bar">
			<Button variant="ghost" size="sm" onclick={() => addMode = 'newTask'}>+ New task</Button>
			<Button variant="ghost" size="sm" onclick={() => addMode = 'newRecord'}>+ New record</Button>
			<Button variant="secondary" size="sm" onclick={() => addMode = 'existing'}>+ Add existing</Button>
		</div>
	{:else}
		<div class="add-panel">
			{#if addMode === 'newTask'}
				<p class="panel-title">New task</p>
				<TextInput bind:value={newTaskName} placeholder="Task name" maxlength={200} autofocus
					onkeydown={(e) => { if (e.key === 'Enter') submitAdd(); if (e.key === 'Escape') cancelAdd(); }} />
				<Textarea bind:value={newTaskDesc} placeholder="Description (optional)" rows={2} />
			{:else if addMode === 'newRecord'}
				<p class="panel-title">New record</p>
				<TextInput bind:value={newRecordTitle} placeholder="Title" maxlength={200} autofocus
					onkeydown={(e) => { if (e.key === 'Enter') submitAdd(); if (e.key === 'Escape') cancelAdd(); }} />
				<Textarea bind:value={newRecordContent} placeholder="Content (optional)" rows={2} />
			{:else if addMode === 'existing'}
				<p class="panel-title">Add existing</p>
				<div class="kind-toggle">
					<Button variant="secondary" size="sm" active={existingKind === 'task'}
						onclick={() => { existingKind = 'task'; selectedExistingId = ''; }}>Tasks</Button>
					<Button variant="secondary" size="sm" active={existingKind === 'record'}
						onclick={() => { existingKind = 'record'; selectedExistingId = ''; }}>Records</Button>
				</div>
				{#if existingKind === 'task'}
					{#if unassignedTasks.length === 0}
						<EmptyState variant="inline">No ungrouped tasks.</EmptyState>
					{:else}
						<Select bind:value={selectedExistingId} options={existingTaskOptions} />
					{/if}
				{:else}
					{#if unassignedRecords.length === 0}
						<EmptyState variant="inline">No ungrouped records.</EmptyState>
					{:else}
						<Select bind:value={selectedExistingId} options={existingRecordOptions} />
					{/if}
				{/if}
			{/if}

			{#if addError}<InlineError>{addError}</InlineError>{/if}

			<div class="panel-actions">
				<Button variant="primary" size="sm" onclick={submitAdd} disabled={adding}>
					{adding ? 'Adding…' : 'Add'}
				</Button>
				<Button variant="secondary" size="sm" onclick={cancelAdd} disabled={adding}>Cancel</Button>
			</div>
		</div>
	{/if}

	<!-- Tasks -->
	<section class="section">
		<SectionHeading>Tasks ({tasks.length})</SectionHeading>
		{#if tasks.length === 0}
			<EmptyState variant="inline">No tasks in this group.</EmptyState>
		{:else}
			<ul class="list">
				{#each tasks as task (task.id)}
					<li>
						<Card accent={task.priority === 'High' ? 'task-high' : task.priority === 'Medium' ? 'task-medium' : 'task-low'} compact>
							<div class="item-row">
								<a href="/tasks/{task.id}" class="item-name" class:done={task.status === 'Done'}>{task.name}</a>
								<Badge variant="priority-{task.priority.toLowerCase() as 'high'|'medium'|'low'}">{task.priority}</Badge>
								<Badge variant={task.status === 'Done' ? 'status-done' : 'status-todo'}>{task.status}</Badge>
								<Button variant="icon" onclick={() => removeTask(task.id)} title="Remove from group">↗</Button>
							</div>
						</Card>
					</li>
				{/each}
			</ul>
		{/if}
	</section>

	<!-- Records -->
	<section class="section">
		<SectionHeading>Records ({records.length})</SectionHeading>
		{#if records.length === 0}
			<EmptyState variant="inline">No records in this group.</EmptyState>
		{:else}
			<ul class="list">
				{#each records as record (record.id)}
					<li>
						<Card accent="record" compact>
							<div class="item-row">
								<a href="/records/{record.id}" class="item-name">{record.title}</a>
								{#if record.content}
									<span class="preview">{record.content.slice(0, 50)}{record.content.length > 50 ? '…' : ''}</span>
								{/if}
								<Button variant="icon" onclick={() => removeRecord(record.id)} title="Remove from group">↗</Button>
							</div>
						</Card>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}

<style>
	.header {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-bottom: var(--space-5);
		flex-wrap: wrap;
	}

	.title {
		flex: 1;
		font-size: 1.3rem;
		font-weight: 700;
		color: var(--color-text-primary);
	}

	.space-row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin-bottom: var(--space-5);
	}

	.space-label {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.add-bar {
		display: flex;
		gap: var(--space-2);
		margin-bottom: var(--space-5);
		flex-wrap: wrap;
	}

	.add-panel {
		background: var(--color-bg-sunken);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		padding: var(--space-4);
		margin-bottom: var(--space-5);
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.panel-title {
		font-size: var(--font-size-base);
		font-weight: 600;
		color: var(--color-accent-text);
	}

	.kind-toggle {
		display: flex;
		gap: var(--space-2);
	}

	.panel-actions {
		display: flex;
		gap: var(--space-2);
	}

	.section {
		margin-bottom: var(--space-8);
	}

	.list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.item-row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.item-name {
		flex: 1;
		font-size: var(--font-size-base);
		color: var(--color-text-primary);
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.item-name.done { opacity: 0.45; text-decoration: line-through; }

	.preview {
		font-size: var(--font-size-xs);
		color: var(--color-text-faint);
		flex-shrink: 0;
	}
</style>
