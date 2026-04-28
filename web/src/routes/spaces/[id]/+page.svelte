<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Group, Space, SpaceMember } from '$lib/types';
	import BackLink from '$lib/ui/BackLink.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import InlineError from '$lib/ui/InlineError.svelte';
	import SectionHeading from '$lib/ui/SectionHeading.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	const id = $derived($page.params.id!);

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
	let memberError = $state<string | null>(null);

	let newGroupTitle = $state('');
	let addingGroup = $state(false);

	async function load() {
		try {
			let s: typeof space = null, gotSpace = false, gotGroups = false;
			function tryReady() { if (gotSpace && gotGroups) loading = false; }

			await Promise.all([
				api.getSpace(id,
					(c) => { space = c; titleDraft = c.title; gotSpace = true; tryReady(); },
					(f) => { space = f; titleDraft = f.title; gotSpace = true; tryReady(); }
				),
				// members not cached (no store for members) — fetch directly
				api.listSpaceMembers(id).then((m) => { members = m; }),
				api.getGroups(
					(c) => { groups = c.filter((g) => g.spaceId === id); gotGroups = true; tryReady(); },
					(f) => { groups = f.filter((g) => g.spaceId === id); gotGroups = true; tryReady(); }
				)
			]);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function saveTitle() {
		if (!titleDraft.trim() || !space) return;
		saving = true;
		try {
			const updated = await api.updateSpace(id, { title: titleDraft.trim() });
			if (updated) space = updated;
			editingTitle = false;
			toast.success('Space renamed');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function addMember() {
		if (!newMemberId.trim()) return;
		addingMember = true;
		memberError = null;
		try {
			const member = await api.addSpaceMember(id, { userId: newMemberId.trim() });
			members = [...members, member];
			newMemberId = '';
			toast.success('Member added');
		} catch (e) {
			memberError = (e as Error).message;
			toast.error((e as Error).message);
		} finally {
			addingMember = false;
		}
	}

	async function removeMember(userId: string) {
		await api.removeSpaceMember(id, userId);
		members = members.filter((m) => m.userId !== userId);
		toast.info('Member removed');
	}

	async function createGroupHere() {
		if (!newGroupTitle.trim()) return;
		addingGroup = true;
		try {
			const group = await api.createGroup({ title: newGroupTitle.trim(), spaceId: id });
			groups = [...groups, group];
			newGroupTitle = '';
			toast.success('Group created');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			addingGroup = false;
		}
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<BackLink href="/spaces" label="Spaces" />

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if space}
	<!-- Title row -->
	<div class="header">
		{#if editingTitle && !space.isPrivate}
			<TextInput bind:value={titleDraft} maxlength={200} size="sm"
				onkeydown={(e) => {
					if (e.key === 'Enter') saveTitle();
					if (e.key === 'Escape') editingTitle = false;
				}} />
			<Button variant="primary" size="sm" onclick={saveTitle} disabled={saving || !titleDraft.trim()}>Save</Button>
			<Button variant="secondary" size="sm" onclick={() => editingTitle = false}>Cancel</Button>
		{:else}
			<h1 class="title">{space.title}</h1>
			{#if space.isPrivate}
				<Badge variant="space-private" pill>Private</Badge>
			{:else}
				<Button variant="secondary" size="sm" onclick={() => { titleDraft = space!.title; editingTitle = true; }}>Rename</Button>
			{/if}
		{/if}
	</div>

	<!-- Members (shared spaces only) -->
	{#if !space.isPrivate}
		<section class="section">
			<SectionHeading>Members</SectionHeading>
			<div class="add-row">
				<TextInput bind:value={newMemberId} placeholder="User ID…" size="sm"
					onkeydown={(e) => e.key === 'Enter' && addMember()} />
				<Button variant="primary" size="sm" onclick={addMember} disabled={addingMember || !newMemberId.trim()}>Add</Button>
			</div>
			{#if memberError}<InlineError>{memberError}</InlineError>{/if}
			{#if members.length === 0}
				<EmptyState variant="inline">No members yet.</EmptyState>
			{:else}
				<ul class="list">
					{#each members as member (member.userId)}
						<li>
							<Card compact>
								<div class="row">
									<span class="member-id">{member.userId}</span>
									<Button variant="icon" onclick={() => removeMember(member.userId)} aria-label="Remove">×</Button>
								</div>
							</Card>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	{/if}

	<!-- Groups -->
	<section class="section">
		<SectionHeading>Groups</SectionHeading>
		<div class="add-row">
			<TextInput bind:value={newGroupTitle} placeholder="New group…" maxlength={200} size="sm"
				onkeydown={(e) => e.key === 'Enter' && createGroupHere()} />
			<Button variant="primary" size="sm" onclick={createGroupHere} disabled={addingGroup || !newGroupTitle.trim()}>
				Create
			</Button>
		</div>
		{#if groups.length === 0}
			<EmptyState variant="inline">No groups in this space.</EmptyState>
		{:else}
			<ul class="list">
				{#each groups as group (group.id)}
					<li>
						<Card accent="group" compact>
							<a href="/groups/{group.id}" class="group-link">{group.title}</a>
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
		margin-bottom: var(--space-8);
		flex-wrap: wrap;
	}

	.title {
		flex: 1;
		font-size: 1.5rem;
		font-weight: 700;
		color: var(--color-text-primary);
	}

	.section {
		margin-bottom: var(--space-8);
	}

	.add-row {
		display: flex;
		gap: var(--space-2);
		margin-bottom: var(--space-3);
		align-items: center;
	}

	.add-row :global(.input) { flex: 1; }

	.list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.row {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.member-id {
		flex: 1;
		font-family: monospace;
		font-size: var(--font-size-base);
		color: var(--color-accent-text);
	}

	.group-link {
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
	}
</style>
