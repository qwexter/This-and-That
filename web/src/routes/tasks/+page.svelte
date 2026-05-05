<script lang="ts">
	import { api } from '$lib/api';
	import type { Task, AddTask, TaskPriority } from '$lib/types';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import CheckCircle from '$lib/ui/CheckCircle.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Select from '$lib/ui/Select.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';
	import { features } from '$lib/features';

	let nameInput: ReturnType<typeof TextInput> | undefined;
	let tasks = $state<Task[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newName = $state('');
	let newPriority = $state<TaskPriority>('Low');
	let adding = $state(false);

	async function load() {
		try {
			await api.getTasks(
				(cached) => { tasks = cached; loading = false; },
				(fresh)  => { tasks = fresh;  loading = false; }
			);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function addTask() {
		if (!newName.trim()) return;
		adding = true;
		try {
			const body: AddTask = { name: newName.trim(), priority: newPriority, description: null, status: null, deadline: null };
			const task = await api.createTask(body);
			tasks = [...tasks, task];
			newName = '';
			toast.success('Task created');
			nameInput?.focus();
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function toggleDone(task: Task) {
		const updated = await api.updateTask(task.id, { status: task.status === 'Done' ? 'Todo' : 'Done' });
		if (updated) tasks = tasks.map((t) => (t.id === updated.id ? updated : t));
		else tasks = tasks.map((t) => t.id === task.id ? { ...t, status: task.status === 'Done' ? 'Todo' : 'Done' } as Task : t);
	}

	async function remove(id: string) {
		await api.deleteTask(id);
		tasks = tasks.filter((t) => t.id !== id);
		toast.success('Task deleted');
	}

	const todo = $derived(tasks.filter((t) => t.status === 'Todo'));
	const done = $derived(tasks.filter((t) => t.status === 'Done'));

	const priorityOptions = [
		{ value: 'Low', label: 'Low' },
		{ value: 'Medium', label: 'Medium' },
		{ value: 'High', label: 'High' }
	];

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<div class="add-form">
	<TextInput bind:this={nameInput} bind:value={newName} placeholder="New task…" onkeydown={(e) => e.key === 'Enter' && addTask()} />
	{#if features.priority}<Select bind:value={newPriority} options={priorityOptions} size="sm" />{/if}
	<Button variant="primary" onclick={addTask} disabled={adding || !newName.trim()}>Add</Button>
</div>

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else}
	<ul class="task-list">
		{#each todo as task (task.id)}
			<li>
				<Card accent={features.priority ? (task.priority === 'High' ? 'task-high' : task.priority === 'Medium' ? 'task-medium' : 'task-low') : 'none'} compact>
					<div class="task-row">
						<CheckCircle checked={false} onclick={() => toggleDone(task)} />
						<a href="/tasks/{task.id}" class="task-name">{task.name}</a>
						{#if features.priority}<Badge variant="priority-{task.priority.toLowerCase() as 'high'|'medium'|'low'}">{task.priority}</Badge>{/if}
						<Button variant="icon" onclick={() => remove(task.id)} aria-label="Delete">×</Button>
					</div>
				</Card>
			</li>
		{/each}
	</ul>

	{#if done.length}
		<details class="done-section">
			<summary>Done ({done.length})</summary>
			<ul class="task-list">
				{#each done as task (task.id)}
					<li>
						<Card accent="none" compact>
							<div class="task-row">
								<CheckCircle checked={true} onclick={() => toggleDone(task)} />
								<a href="/tasks/{task.id}" class="task-name done">{task.name}</a>
								<Button variant="icon" onclick={() => remove(task.id)} aria-label="Delete">×</Button>
							</div>
						</Card>
					</li>
				{/each}
			</ul>
		</details>
	{/if}

	{#if tasks.length === 0}
		<EmptyState variant="page">No tasks yet.</EmptyState>
	{/if}
{/if}

<style>
	.add-form {
		display: flex;
		gap: var(--space-2);
		margin-bottom: var(--space-6);
		align-items: center;
	}

	.add-form :global(.input) { flex: 1; }

	.task-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.task-row {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.task-name {
		flex: 1;
		font-size: var(--font-size-base);
		color: var(--color-text-primary);
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.task-name.done {
		opacity: 0.45;
		text-decoration: line-through;
	}

	.done-section {
		margin-top: var(--space-6);
	}

	.done-section summary {
		cursor: pointer;
		color: var(--color-text-muted);
		margin-bottom: var(--space-3);
		font-size: var(--font-size-base);
		user-select: none;
	}
</style>
