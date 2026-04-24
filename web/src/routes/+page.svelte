<script lang="ts">
	import { api } from '$lib/api';
	import type { Task, AddTask, TaskPriority } from '$lib/types';

	let tasks = $state<Task[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let newName = $state('');
	let newPriority = $state<TaskPriority>('Low');
	let adding = $state(false);

	async function load() {
		try {
			tasks = await api.getTasks();
		} catch (e) {
			error = (e as Error).message;
		} finally {
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
		} catch (e) {
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function toggleDone(task: Task) {
		const updated = await api.updateTask(task.id, {
			status: task.status === 'Done' ? 'Todo' : 'Done'
		});
		tasks = tasks.map((t) => (t.id === updated.id ? updated : t));
	}

	async function remove(id: string) {
		await api.deleteTask(id);
		tasks = tasks.filter((t) => t.id !== id);
	}

	const todo = $derived(tasks.filter((t) => t.status === 'Todo'));
	const done = $derived(tasks.filter((t) => t.status === 'Done'));

	$effect(() => { load(); });
</script>

<section class="add-form">
	<input
		bind:value={newName}
		placeholder="New task…"
		onkeydown={(e) => e.key === 'Enter' && addTask()}
	/>
	<select bind:value={newPriority}>
		<option value="Low">Low</option>
		<option value="Medium">Medium</option>
		<option value="High">High</option>
	</select>
	<button onclick={addTask} disabled={adding || !newName.trim()}>Add</button>
</section>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else}
	<ul class="task-list">
		{#each todo as task (task.id)}
			<li class="task" data-priority={task.priority.toLowerCase()}>
				<button class="check" onclick={() => toggleDone(task)} aria-label="Mark done"></button>
				<a href="/tasks/{task.id}" class="task-name">{task.name}</a>
				<span class="badge">{task.priority}</span>
				<button class="del" onclick={() => remove(task.id)} aria-label="Delete">×</button>
			</li>
		{/each}
	</ul>

	{#if done.length}
		<details class="done-section">
			<summary>Done ({done.length})</summary>
			<ul class="task-list">
				{#each done as task (task.id)}
					<li class="task done" data-priority={task.priority.toLowerCase()}>
						<button class="check checked" onclick={() => toggleDone(task)} aria-label="Mark todo"></button>
						<a href="/tasks/{task.id}" class="task-name">{task.name}</a>
						<button class="del" onclick={() => remove(task.id)} aria-label="Delete">×</button>
					</li>
				{/each}
			</ul>
		</details>
	{/if}
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

	.add-form select {
		padding: 0.5rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
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

	.state.error {
		color: #f87171;
	}

	.task-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.task {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.75rem;
		background: #16213e;
		border-radius: 8px;
		border-left: 3px solid transparent;
	}

	.task[data-priority='high'] { border-left-color: #f87171; }
	.task[data-priority='medium'] { border-left-color: #fbbf24; }
	.task[data-priority='low'] { border-left-color: #34d399; }

	.task.done .task-name {
		opacity: 0.4;
		text-decoration: line-through;
	}

	.task-name {
		flex: 1;
		font-size: 0.95rem;
	}

	.badge {
		font-size: 0.7rem;
		padding: 0.1rem 0.4rem;
		background: #2a2a4a;
		border-radius: 4px;
		color: #aaa;
	}

	.check {
		width: 20px;
		height: 20px;
		border-radius: 50%;
		border: 2px solid #4f46e5;
		background: transparent;
		cursor: pointer;
		flex-shrink: 0;
	}

	.check.checked {
		background: #4f46e5;
	}

	.del {
		background: transparent;
		border: none;
		color: #888;
		cursor: pointer;
		font-size: 1.2rem;
		line-height: 1;
		padding: 0 0.25rem;
	}

	.del:hover { color: #f87171; }

	.done-section {
		margin-top: 1.5rem;
	}

	.done-section summary {
		cursor: pointer;
		color: #888;
		margin-bottom: 0.75rem;
		font-size: 0.9rem;
	}
</style>
