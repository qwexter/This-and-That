<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Task, TaskPriority, TaskStatus } from '$lib/types';
	import BackLink from '$lib/ui/BackLink.svelte';
	import Button from '$lib/ui/Button.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import FormField from '$lib/ui/FormField.svelte';
	import Select from '$lib/ui/Select.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

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

	function applyTask(t: typeof task & NonNullable<unknown>) {
		task = t;
		editName = t.name;
		editDescription = t.description ?? '';
		editPriority = t.priority;
		editStatus = t.status;
		editDeadline = t.deadline ?? '';
		loading = false;
	}

	async function load() {
		try {
			await api.getTask(id, applyTask, applyTask);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function save() {
		if (!editName.trim()) return;
		saving = true;
		try {
			const updated = await api.updateTask(id, {
				name: editName.trim(),
				description: editDescription || null,
				priority: editPriority,
				status: editStatus,
				deadline: editDeadline || null
			});
			if (updated) task = updated;
			toast.success('Task saved');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function remove() {
		await api.deleteTask(id);
		toast.success('Task deleted');
		goto('/');
	}

	const priorityOptions = [
		{ value: 'Low', label: 'Low' },
		{ value: 'Medium', label: 'Medium' },
		{ value: 'High', label: 'High' }
	];

	const statusOptions = [
		{ value: 'Todo', label: 'Todo' },
		{ value: 'Done', label: 'Done' }
	];

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<BackLink href="/" label="Feed" />

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if task}
	<form class="form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<FormField label="Name" id="name">
			<TextInput id="name" bind:value={editName} maxlength={200} />
		</FormField>

		<FormField label="Description" id="desc">
			<Textarea id="desc" bind:value={editDescription} rows={3} />
		</FormField>

		<div class="row">
			<FormField label="Priority" id="priority">
				<Select id="priority" bind:value={editPriority} options={priorityOptions} />
			</FormField>
			<FormField label="Status" id="status">
				<Select id="status" bind:value={editStatus} options={statusOptions} />
			</FormField>
		</div>

		<FormField label="Deadline" id="deadline">
			<TextInput id="deadline" type="datetime-local" bind:value={editDeadline} />
		</FormField>

		<div class="actions">
			<Button type="submit" variant="primary" disabled={saving}>
				{saving ? 'Saving…' : 'Save'}
			</Button>
			<Button type="button" variant="danger" onclick={remove}>Delete</Button>
		</div>
	</form>
{/if}

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-4);
	}

	.actions {
		display: flex;
		gap: var(--space-3);
		margin-top: var(--space-2);
	}
</style>
