<script lang="ts">
	import { api } from '$lib/api';
	import type { Group, Space } from '$lib/types';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Select from '$lib/ui/Select.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	let titleInput: ReturnType<typeof TextInput> | undefined;
	let groups = $state<Group[]>([]);
	let spaces = $state<Space[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let newSpaceId = $state('');
	let adding = $state(false);

	const spaceMap = $derived(new Map(spaces.map((s) => [s.id, s])));
	const nonPrivateSpaces = $derived(spaces.filter((s) => !s.isPrivate));

	const spaceOptions = $derived([
		{ value: '', label: 'Private' },
		...nonPrivateSpaces.map(s => ({ value: s.id, label: s.title }))
	]);

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
			// run both SWR fetches in parallel; set loading=false on first data from either
			let gotGroups = false, gotSpaces = false;
			function maybeReady() { if (gotGroups && gotSpaces) loading = false; }
			await Promise.all([
				api.getGroups(
					(cached) => { groups = cached; gotGroups = true; maybeReady(); },
					(fresh)  => { groups = fresh;  gotGroups = true; maybeReady(); }
				),
				api.getSpaces(
					(cached) => { spaces = cached; gotSpaces = true; maybeReady(); },
					(fresh)  => { spaces = fresh;  gotSpaces = true; maybeReady(); }
				)
			]);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function addGroup() {
		if (!newTitle.trim()) return;
		adding = true;
		try {
			const group = await api.createGroup({ title: newTitle.trim(), spaceId: newSpaceId || null });
			groups = [...groups, group];
			newTitle = '';
			newSpaceId = '';
			toast.success('Group created');
			titleInput?.focus();
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteGroup(id);
		groups = groups.filter((g) => g.id !== id);
		toast.success('Group deleted');
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<div class="add-form">
	<TextInput bind:this={titleInput} bind:value={newTitle} placeholder="New group…" maxlength={200}
		onkeydown={(e) => e.key === 'Enter' && addGroup()} />
	{#if nonPrivateSpaces.length > 0}
		<Select bind:value={newSpaceId} options={spaceOptions} size="sm" />
	{/if}
	<Button variant="primary" onclick={addGroup} disabled={adding || !newTitle.trim()}>Add</Button>
</div>

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if groups.length === 0}
	<EmptyState variant="page">No groups yet.</EmptyState>
{:else}
	<ul class="list">
		{#each groups as group (group.id)}
			<li>
				<Card accent="group" compact>
					<div class="row">
						<a href="/groups/{group.id}" class="title">{group.title}</a>
						<Badge variant={spaceIsPrivate(group.spaceId) ? 'space-private' : 'space-shared'} pill>
							{spaceName(group.spaceId)}
						</Badge>
						<Button variant="icon" onclick={() => remove(group.id)} aria-label="Delete">×</Button>
					</div>
				</Card>
			</li>
		{/each}
	</ul>
{/if}

<style>
	.add-form {
		display: flex;
		gap: var(--space-2);
		margin-bottom: var(--space-6);
		align-items: center;
		flex-wrap: wrap;
	}

	.add-form :global(.input) { flex: 1; min-width: 8rem; }

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

	.title {
		flex: 1;
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
	}
</style>
