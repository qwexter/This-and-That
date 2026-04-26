<script lang="ts">
	import { api } from '$lib/api';
	import type { Group, Space } from '$lib/types';

	let groups = $state<Group[]>([]);
	let spaces = $state<Space[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let newSpaceId = $state('');
	let adding = $state(false);

	const spaceMap = $derived(new Map(spaces.map((s) => [s.id, s])));
	const nonPrivateSpaces = $derived(spaces.filter((s) => !s.isPrivate));

	function spaceName(spaceId: string | null): string {
		if (!spaceId) return 'Private';
		return spaceMap.get(spaceId)?.title ?? 'Private';
	}

	function spaceIsPrivate(spaceId: string | null): boolean {
		if (!spaceId) return true;
		return spaceMap.get(spaceId)?.isPrivate ?? true;
	}

	async function load() {
		try {
			[groups, spaces] = await Promise.all([api.getGroups(), api.getSpaces()]);
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
			const group = await api.createGroup({
				title: newTitle.trim(),
				spaceId: newSpaceId || null
			});
			groups = [...groups, group];
			newTitle = '';
			newSpaceId = '';
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
	{#if nonPrivateSpaces.length > 0}
		<select bind:value={newSpaceId} class="space-select">
			<option value="">Private</option>
			{#each nonPrivateSpaces as s (s.id)}
				<option value={s.id}>{s.title}</option>
			{/each}
		</select>
	{/if}
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
				<span class="space-badge" class:private={spaceIsPrivate(group.spaceId)}>
					{spaceName(group.spaceId)}
				</span>
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
		flex-wrap: wrap;
	}

	.add-form input {
		flex: 1;
		min-width: 8rem;
		padding: 0.5rem 0.75rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 1rem;
	}

	.space-select {
		padding: 0.5rem 0.6rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.9rem;
		cursor: pointer;
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

	.space-badge {
		font-size: 0.7rem;
		padding: 0.15rem 0.5rem;
		background: #1e3a5f;
		color: #60a5fa;
		border-radius: 999px;
		white-space: nowrap;
	}

	.space-badge.private {
		background: #2a2a4a;
		color: #888;
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
