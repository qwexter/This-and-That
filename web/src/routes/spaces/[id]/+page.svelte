<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Group, Space, SpaceMember } from '$lib/types';

	const id = $derived($page.params.id);

	let space = $state<Space | null>(null);
	let members = $state<SpaceMember[]>([]);
	let groups = $state<Group[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let editingTitle = $state(false);
	let titleDraft = $state('');
	let saving = $state(false);

	let newMemberId = $state('');
	let addingMember = $state(false);

	let newGroupTitle = $state('');
	let addingGroup = $state(false);

	async function load() {
		try {
			const [s, m, allGroups] = await Promise.all([
				api.getSpace(id),
				api.listSpaceMembers(id),
				api.getGroups()
			]);
			space = s;
			members = m;
			groups = allGroups.filter((g) => g.spaceId === id);
			titleDraft = s.title;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function createGroupHere() {
		if (!newGroupTitle.trim()) return;
		addingGroup = true;
		try {
			const group = await api.createGroup({ title: newGroupTitle.trim(), spaceId: id });
			groups = [...groups, group];
			newGroupTitle = '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			addingGroup = false;
		}
	}

	async function saveTitle() {
		if (!titleDraft.trim() || !space) return;
		saving = true;
		try {
			space = await api.updateSpace(id, { title: titleDraft.trim() });
			editingTitle = false;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function addMember() {
		if (!newMemberId.trim()) return;
		addingMember = true;
		try {
			const member = await api.addSpaceMember(id, { userId: newMemberId.trim() });
			members = [...members, member];
			newMemberId = '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			addingMember = false;
		}
	}

	async function removeMember(userId: string) {
		await api.removeSpaceMember(id, userId);
		members = members.filter((m) => m.userId !== userId);
	}

	$effect(() => { load(); });
</script>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if space}
	<div class="header">
		{#if editingTitle}
			<input
				bind:value={titleDraft}
				class="title-input"
				maxlength="200"
				onkeydown={(e) => { if (e.key === 'Enter') saveTitle(); if (e.key === 'Escape') editingTitle = false; }}
			/>
			<button onclick={saveTitle} disabled={saving || !titleDraft.trim()}>Save</button>
			<button class="secondary" onclick={() => editingTitle = false}>Cancel</button>
		{:else}
			<h1>{space.title}</h1>
			{#if !space.isPrivate}
				<button class="secondary" onclick={() => { titleDraft = space!.title; editingTitle = true; }}>Rename</button>
			{/if}
		{/if}
	</div>

	{#if !space.isPrivate}
	<section class="section">
		<h2>Members</h2>
		<div class="add-form">
			<input
				bind:value={newMemberId}
				placeholder="User ID…"
				onkeydown={(e) => e.key === 'Enter' && addMember()}
			/>
			<button onclick={addMember} disabled={addingMember || !newMemberId.trim()}>Add</button>
		</div>
		{#if members.length === 0}
			<p class="empty">No members yet.</p>
		{:else}
			<ul class="member-list">
				{#each members as member (member.userId)}
					<li class="member-item">
						<span class="member-id">{member.userId}</span>
						<button class="del" onclick={() => removeMember(member.userId)} aria-label="Remove">×</button>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
	{/if}

	<section class="section">
		<h2>Groups</h2>
		<div class="add-form">
			<input
				bind:value={newGroupTitle}
				placeholder="New group title…"
				maxlength="200"
				onkeydown={(e) => e.key === 'Enter' && createGroupHere()}
			/>
			<button onclick={createGroupHere} disabled={addingGroup || !newGroupTitle.trim()}>Create here</button>
		</div>
		{#if groups.length === 0}
			<p class="empty">No groups in this space.</p>
		{:else}
			<ul class="group-list">
				{#each groups as group (group.id)}
					<li class="group-item">
						<a href="/groups/{group.id}" class="group-link">{group.title}</a>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}

<style>
	.state { text-align: center; color: #888; padding: 2rem 0; }
	.state.error { color: #f87171; }

	.header {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		margin-bottom: 2rem;
	}

	h1 {
		flex: 1;
		font-size: 1.5rem;
		font-weight: 700;
	}

	.title-input {
		flex: 1;
		padding: 0.4rem 0.75rem;
		background: #16213e;
		border: 1px solid #4f46e5;
		border-radius: 6px;
		color: inherit;
		font-size: 1.1rem;
	}

	button {
		padding: 0.4rem 0.9rem;
		background: #4f46e5;
		border: none;
		border-radius: 6px;
		color: #fff;
		cursor: pointer;
		font-size: 0.85rem;
	}

	button:disabled { opacity: 0.4; cursor: default; }

	button.secondary {
		background: #2a2a4a;
	}

	.section {
		margin-bottom: 2rem;
	}

	h2 {
		font-size: 1rem;
		font-weight: 600;
		color: #888;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		margin-bottom: 0.75rem;
	}

	.add-form {
		display: flex;
		gap: 0.5rem;
		margin-bottom: 1rem;
	}

	.add-form input {
		flex: 1;
		padding: 0.5rem 0.75rem;
		background: #16213e;
		border: 1px solid #2a2a4a;
		border-radius: 6px;
		color: inherit;
		font-size: 0.9rem;
	}

	.empty { color: #888; font-size: 0.9rem; }

	.member-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}

	.member-item {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.6rem 1rem;
		background: #16213e;
		border-radius: 6px;
	}

	.member-id {
		flex: 1;
		font-size: 0.9rem;
		font-family: monospace;
		color: #a5b4fc;
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

	.group-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}

	.group-item {
		padding: 0.6rem 1rem;
		background: #16213e;
		border-radius: 6px;
		border-left: 3px solid #818cf8;
	}

	.group-link {
		font-size: 0.9rem;
		font-weight: 500;
	}
</style>
