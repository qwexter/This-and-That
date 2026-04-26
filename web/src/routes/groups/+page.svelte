<script lang="ts">
	import { api } from '$lib/api';
	import type { Group } from '$lib/types';

	let groups = $state<Group[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let adding = $state(false);

	async function load() {
		try {
			groups = await api.getGroups();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function addGroup() {
		if (!newTitle.trim()) return;
		adding = true;
		try {
			const group = await api.createGroup({ title: newTitle.trim() });
			groups = [...groups, group];
			newTitle = '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteGroup(id);
		groups = groups.filter((g) => g.id !== id);
	}

	$effect(() => { load(); });
</script>

<section class="add-form">
	<input
		bind:value={newTitle}
		placeholder="New group title…"
		maxlength="200"
		onkeydown={(e) => e.key === 'Enter' && addGroup()}
	/>
	<button onclick={addGroup} disabled={adding || !newTitle.trim()}>Add</button>
</section>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if groups.length === 0}
	<p class="state">No groups yet.</p>
{:else}
	<ul class="group-list">
		{#each groups as group (group.id)}
			<li class="group-item">
				<a href="/groups/{group.id}" class="group-title">{group.title}</a>
				<button class="del" onclick={() => remove(group.id)} aria-label="Delete">×</button>
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

	.add-form button:disabled { opacity: 0.4; cursor: default; }

	.state { text-align: center; color: #888; padding: 2rem 0; }
	.state.error { color: #f87171; }

	.group-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.group-item {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.75rem 1rem;
		background: #16213e;
		border-radius: 8px;
		border-left: 3px solid #818cf8;
	}

	.group-title {
		flex: 1;
		font-size: 0.95rem;
		font-weight: 500;
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
</style>
