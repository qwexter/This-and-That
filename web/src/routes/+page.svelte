<script lang="ts">
	import { api } from '$lib/api';
	import { goto } from '$app/navigation';
	import type { FeedEntry, FeedPage, Group, Space, TaskPriority } from '$lib/types';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Select from '$lib/ui/Select.svelte';
	import InlineError from '$lib/ui/InlineError.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';
	import { features } from '$lib/features';

	// ── Data ────────────────────────────────────────────────────────────
	let spaces = $state<Space[]>([]);
	let groups = $state<Group[]>([]);
	let items = $state<FeedEntry[]>([]);
	let page = $state<FeedPage | null>(null);
	let loading = $state(true);
	let loadingMore = $state(false);
	let error = $state<string | null>(null);

	const LIMIT = 20;
	const TAB_KEY = 'tat-active-tab';

	// 'all' or a space id
	let activeTab = $state<string>(
		typeof localStorage !== 'undefined' ? (localStorage.getItem(TAB_KEY) ?? 'all') : 'all'
	);

	const showTabs = $derived(spaces.length > 1);
	const privateSpace = $derived(spaces.find(s => s.isPrivate) ?? null);

	// active space for context-aware FAB defaults
	const activeSpace = $derived(
		activeTab === 'all'
			? (privateSpace ?? null)
			: (spaces.find(s => s.id === activeTab) ?? null)
	);

	function selectTab(id: string) {
		activeTab = id;
		localStorage.setItem(TAB_KEY, id);
		items = [];
		page = null;
		loading = true;
		loadFeed(0);
	}

	async function loadFeed(offset = 0) {
		const spaceId = activeTab === 'all' ? null : activeTab;
		if (offset === 0) {
			await api.getFeed({ limit: LIMIT, offset, spaceId },
				(cached) => { items = cached.items; page = cached; loading = false; },
				(fresh)  => { items = fresh.items;  page = fresh;  loading = false; }
			);
		} else {
			const result = await api.getFeed({ limit: LIMIT, offset, spaceId }, undefined, undefined);
			items = [...items, ...result.items];
			page = result;
		}
	}

	async function loadAll() {
		try {
			await Promise.all([
				api.getSpaces(
					(cached) => { spaces = cached; },
					(fresh)  => {
						spaces = fresh;
						// if saved tab no longer exists, reset to 'all'
						if (activeTab !== 'all' && !fresh.find(s => s.id === activeTab)) {
							activeTab = 'all';
							localStorage.setItem(TAB_KEY, 'all');
						}
					}
				),
				api.getGroups(
					(cached) => { groups = cached; },
					(fresh)  => { groups = fresh; }
				),
				loadFeed(0)
			]);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function loadMore() {
		loadingMore = true;
		try {
			await loadFeed(items.length);
		} finally {
			loadingMore = false;
		}
	}

	const hasMore = $derived(page != null && items.length < page.total);

	$effect(() => { loadAll(); });

	$effect(() => {
		return connection.onReconnect(() => {
			loadAll();
		});
	});

	// ── FAB ─────────────────────────────────────────────────────────────
	type FabMode = 'closed' | 'menu' | 'task' | 'record' | 'group' | 'space';
	let fabMode = $state<FabMode>('closed');
	let fabError = $state<string | null>(null);
	let fabBusy = $state(false);

	function onSheetKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			if (fabMode === 'menu') closeFab();
			else openFab('menu');
		}
	}

	// task form
	let taskName = $state('');
	let taskPriority = $state<TaskPriority>('Low');
	let taskGroupId = $state('');

	// record form
	let recordTitle = $state('');
	let recordContent = $state('');
	let recordGroupId = $state('');

	// group form
	let groupTitle = $state('');
	let groupSpaceId = $state('');

	// space form
	let spaceTitle = $state('');

	const nonPrivateSpaces = $derived(spaces.filter(s => !s.isPrivate));

	// groups in active shared space (empty if private/all tab)
	const activeSpaceGroups = $derived(
		activeSpace && !activeSpace.isPrivate
			? groups.filter(g => g.spaceId === activeSpace!.id)
			: []
	);
	// whether we need a group to attach new items (shared space selected)
	const requiresGroup = $derived(activeSpace != null && !activeSpace.isPrivate);

	function openFab(mode: FabMode) {
		fabMode = mode;
		fabError = null;
		taskName = '';
		taskPriority = 'Low';
		taskGroupId = activeSpaceGroups.length > 0 ? activeSpaceGroups[0].id : '';
		recordTitle = '';
		recordContent = '';
		recordGroupId = activeSpaceGroups.length > 0 ? activeSpaceGroups[0].id : '';
		groupTitle = '';
		// pre-fill group space from active tab context
		groupSpaceId = activeSpace && !activeSpace.isPrivate ? activeSpace.id : '';
		spaceTitle = '';
	}

	function closeFab() { fabMode = 'closed'; fabError = null; }

	async function submitTask() {
		if (!taskName.trim()) { fabError = 'Name required'; return; }
		if (requiresGroup && !taskGroupId) { fabError = 'Select a group (required for shared spaces)'; return; }
		fabBusy = true;
		try {
			const task = await api.createTask({
				name: taskName.trim(),
				priority: taskPriority,
				description: null,
				status: null,
				deadline: null,
				groupId: taskGroupId || null,
			});
			closeFab();
			toast.success('Task created');
			await loadFeed(0);
			goto(`/tasks/${task.id}`);
		} catch (e) {
			fabError = (e as Error).message;
			toast.error((e as Error).message);
		} finally {
			fabBusy = false;
		}
	}

	async function submitRecord() {
		if (!recordTitle.trim()) { fabError = 'Title required'; return; }
		if (requiresGroup && !recordGroupId) { fabError = 'Select a group (required for shared spaces)'; return; }
		fabBusy = true;
		try {
			const rec = await api.createRecord({
				title: recordTitle.trim(),
				content: recordContent.trim() || null,
				groupId: recordGroupId || null,
			});
			closeFab();
			toast.success('Record created');
			await loadFeed(0);
			goto(`/records/${rec.id}`);
		} catch (e) {
			fabError = (e as Error).message;
		} finally {
			fabBusy = false;
		}
	}

	async function submitGroup() {
		if (!groupTitle.trim()) { fabError = 'Title required'; return; }
		fabBusy = true;
		try {
			const grp = await api.createGroup({ title: groupTitle.trim(), spaceId: groupSpaceId || null });
			closeFab();
			toast.success('Group created');
			await Promise.all([
				loadFeed(0),
				api.getGroups(undefined, (fresh) => { groups = fresh; }),
			]);
			goto(`/groups/${grp.id}`);
		} catch (e) {
			fabError = (e as Error).message;
		} finally {
			fabBusy = false;
		}
	}

	async function submitSpace() {
		if (!spaceTitle.trim()) { fabError = 'Title required'; return; }
		fabBusy = true;
		try {
			const sp = await api.createSpace({ title: spaceTitle.trim() });
			spaces = [...spaces, sp];
			closeFab();
			toast.success('Space created');
			goto(`/spaces/${sp.id}`);
		} catch (e) {
			fabError = (e as Error).message;
		} finally {
			fabBusy = false;
		}
	}

	// ── Helpers ──────────────────────────────────────────────────────────
	function formatDate(iso: string): string {
		const diff = Date.now() - new Date(iso).getTime();
		if (diff < 60_000) return 'just now';
		if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
		if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
		return `${Math.floor(diff / 86_400_000)}d ago`;
	}

	function taskAccent(priority: TaskPriority): 'task-high' | 'task-medium' | 'task-low' {
		if (priority === 'High') return 'task-high';
		if (priority === 'Medium') return 'task-medium';
		return 'task-low';
	}

	const priorityOptions = [
		{ value: 'Low', label: 'Low' },
		{ value: 'Medium', label: 'Medium' },
		{ value: 'High', label: 'High' }
	];
</script>

<!-- ── Space tabs ─────────────────────────────────────────────────────── -->
{#if features.spaces && showTabs}
	<div class="spaces-bar" role="tablist">
		<button
			role="tab"
			aria-selected={activeTab === 'all'}
			class="space-chip"
			class:active={activeTab === 'all'}
			onclick={() => selectTab('all')}
		>All</button>
		{#each spaces as space (space.id)}
			<button
				role="tab"
				aria-selected={activeTab === space.id}
				class="space-chip"
				class:active={activeTab === space.id}
				class:private={space.isPrivate}
				onclick={() => selectTab(space.id)}
			>{space.isPrivate ? 'My' : space.title}</button>
		{/each}
	</div>
{/if}

<!-- ── Feed ─────────────────────────────────────────────────────────── -->
{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if items.length === 0}
	<EmptyState variant="page">Nothing here yet. Create something with the + button.</EmptyState>
{:else}
	<div class="feed">
		{#each items as entry (entry.kind + entry.id)}
			{#if entry.kind === 'group' && features.groups}
				<Card accent="group">
					<div class="card-header">
						<Badge variant="kind-group">group</Badge>
						<a href="/groups/{entry.id}" class="card-title">{entry.title}</a>
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if entry.children.length > 0}
						<ul class="children">
							{#each entry.children as child (child.kind + child.id)}
								{#if child.kind === 'task'}
									<li class="child" data-priority={features.priority ? child.priority.toLowerCase() : ''}>
										<span class="child-icon">✓</span>
										<a href="/tasks/{child.id}" class="child-name" class:done={child.status === 'Done'}>{child.name}</a>
										{#if features.priority}<Badge variant="priority-{child.priority.toLowerCase() as 'high'|'medium'|'low'}">{child.priority}</Badge>{/if}
										{#if features.deadline && child.deadline}
											<span class="child-deadline">{child.deadline.slice(0, 10)}</span>
										{/if}
									</li>
								{:else if features.records}
									<li class="child record-child">
										<span class="child-icon rec">≡</span>
										<a href="/records/{child.id}" class="child-name">{child.title}</a>
										{#if child.content}
											<span class="child-preview">{child.content.slice(0, 60)}{child.content.length > 60 ? '…' : ''}</span>
										{/if}
									</li>
								{/if}
							{/each}
						</ul>
					{:else}
						<p class="empty-group">Empty group</p>
					{/if}
				</Card>

			{:else if entry.kind === 'task'}
				<Card accent={features.priority ? taskAccent(entry.priority) : 'none'}>
					<div class="card-header">
						<Badge variant="kind-task">task</Badge>
						<a href="/tasks/{entry.id}" class="card-title" class:done={entry.status === 'Done'}>{entry.name}</a>
						{#if features.priority}<Badge variant="priority-{entry.priority.toLowerCase() as 'high'|'medium'|'low'}">{entry.priority}</Badge>{/if}
						<Badge variant={entry.status === 'Done' ? 'status-done' : 'status-todo'}>{entry.status}</Badge>
						{#if features.deadline && entry.deadline}
							<span class="card-deadline">{entry.deadline.slice(0, 10)}</span>
						{/if}
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if features.description && entry.description}
						<p class="card-desc">{entry.description}</p>
					{/if}
				</Card>

			{:else if entry.kind === 'record' && features.records}
				<Card accent="record">
					<div class="card-header">
						<Badge variant="kind-record">record</Badge>
						<a href="/records/{entry.id}" class="card-title">{entry.title}</a>
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if entry.content}
						<p class="card-desc">{entry.content.slice(0, 120)}{entry.content.length > 120 ? '…' : ''}</p>
					{/if}
				</Card>
			{/if}
		{/each}
	</div>

	{#if hasMore}
		<div class="load-more">
			<Button variant="secondary" onclick={loadMore} disabled={loadingMore}>
				{loadingMore ? 'Loading…' : `Load more (${page!.total - items.length})`}
			</Button>
		</div>
	{/if}
{/if}

<!-- ── FAB ───────────────────────────────────────────────────────────── -->
{#if fabMode !== 'closed'}
	<!-- backdrop -->
	<button class="backdrop" onclick={closeFab} aria-label="Close"></button>

	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<div class="fab-sheet" role="dialog" tabindex="-1" onkeydown={onSheetKeydown}>
		{#if fabMode === 'menu'}
			<div class="fab-menu">
				<button class="fab-option task" onclick={() => openFab('task')}>
					<span class="fab-option-icon">✓</span>
					<span>Task</span>
				</button>
				{#if features.records}
				<button class="fab-option record" onclick={() => openFab('record')}>
					<span class="fab-option-icon">≡</span>
					<span>Record</span>
				</button>
				{/if}
				{#if features.groups}
				<button class="fab-option group" onclick={() => openFab('group')}>
					<span class="fab-option-icon">⊞</span>
					<span>Group</span>
				</button>
				{/if}
				{#if features.spaces}
				<button class="fab-option space" onclick={() => openFab('space')}>
					<span class="fab-option-icon">◈</span>
					<span>Space</span>
				</button>
				{/if}
			</div>

		{:else if fabMode === 'task'}
			<div class="fab-form">
				<h3 class="form-title">New task</h3>
				{#if requiresGroup && activeSpaceGroups.length === 0}
					<p class="space-nudge">
						Items in a shared space must belong to a group.
						<a href="/groups" onclick={closeFab}>Create a group</a> in <strong>{activeSpace!.title}</strong> first.
					</p>
					<div class="form-actions">
						<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
					</div>
				{:else}
					<TextInput bind:value={taskName} placeholder="Task name" maxlength={200} autofocus
						onkeydown={(e) => e.key === 'Enter' && submitTask()} />
					{#if features.priority}<Select bind:value={taskPriority} options={priorityOptions} />{/if}
					{#if requiresGroup}
						<Select
							bind:value={taskGroupId}
							options={activeSpaceGroups.map(g => ({ value: g.id, label: g.title }))}
						/>
					{/if}
					{#if fabError}<InlineError>{fabError}</InlineError>{/if}
					<div class="form-actions">
						<Button variant="primary" onclick={submitTask} disabled={fabBusy}>
							{fabBusy ? 'Creating…' : 'Create task'}
						</Button>
						<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
					</div>
				{/if}
			</div>

		{:else if fabMode === 'record'}
			<div class="fab-form">
				<h3 class="form-title">New record</h3>
				{#if requiresGroup && activeSpaceGroups.length === 0}
					<p class="space-nudge">
						Items in a shared space must belong to a group.
						<a href="/groups" onclick={closeFab}>Create a group</a> in <strong>{activeSpace!.title}</strong> first.
					</p>
					<div class="form-actions">
						<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
					</div>
				{:else}
					<TextInput bind:value={recordTitle} placeholder="Title" maxlength={200} autofocus
						onkeydown={(e) => e.key === 'Enter' && submitRecord()} />
					<Textarea bind:value={recordContent} placeholder="Content (optional)" rows={3} />
					{#if requiresGroup}
						<Select
							bind:value={recordGroupId}
							options={activeSpaceGroups.map(g => ({ value: g.id, label: g.title }))}
						/>
					{/if}
					{#if fabError}<InlineError>{fabError}</InlineError>{/if}
					<div class="form-actions">
						<Button variant="primary" onclick={submitRecord} disabled={fabBusy}>
							{fabBusy ? 'Creating…' : 'Create record'}
						</Button>
						<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
					</div>
				{/if}
			</div>

		{:else if fabMode === 'group'}
			<div class="fab-form">
				<h3 class="form-title">New group</h3>
				<TextInput bind:value={groupTitle} placeholder="Group title" maxlength={200} autofocus
					onkeydown={(e) => e.key === 'Enter' && submitGroup()} />
				{#if nonPrivateSpaces.length > 0}
					<Select
						bind:value={groupSpaceId}
						options={[{ value: '', label: 'Private space' }, ...nonPrivateSpaces.map(s => ({ value: s.id, label: s.title }))]}
					/>
				{/if}
				{#if fabError}<InlineError>{fabError}</InlineError>{/if}
				<div class="form-actions">
					<Button variant="primary" onclick={submitGroup} disabled={fabBusy}>
						{fabBusy ? 'Creating…' : 'Create group'}
					</Button>
					<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
				</div>
			</div>

		{:else if fabMode === 'space'}
			<div class="fab-form">
				<h3 class="form-title">New space</h3>
				<TextInput bind:value={spaceTitle} placeholder="Space name" maxlength={200} autofocus
					onkeydown={(e) => e.key === 'Enter' && submitSpace()} />
				{#if fabError}<InlineError>{fabError}</InlineError>{/if}
				<div class="form-actions">
					<Button variant="primary" onclick={submitSpace} disabled={fabBusy}>
						{fabBusy ? 'Creating…' : 'Create space'}
					</Button>
					<Button variant="secondary" onclick={() => openFab('menu')}>Back</Button>
				</div>
			</div>
		{/if}
	</div>
{/if}

<!-- FAB trigger -->
<button class="fab" onclick={() => openFab(fabMode === 'closed' ? 'menu' : 'closed')} aria-label="Create new">
	<span class="fab-icon" class:open={fabMode !== 'closed'}>+</span>
</button>

<style>
	/* ── Space tabs ─────────────────────────────────────────────── */
	.spaces-bar {
		display: flex;
		flex-wrap: nowrap;
		gap: var(--space-1);
		margin-bottom: var(--space-5);
		overflow-x: auto;
		scrollbar-width: none;
		-webkit-overflow-scrolling: touch;
		padding-bottom: 2px;
	}

	.spaces-bar::-webkit-scrollbar { display: none; }

	.space-chip {
		display: inline-flex;
		align-items: center;
		padding: 0.3rem 0.9rem;
		border-radius: var(--radius-pill);
		font-size: var(--font-size-sm);
		font-weight: 500;
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		cursor: pointer;
		white-space: nowrap;
		font-family: inherit;
		transition: background 0.12s, color 0.12s, border-color 0.12s;
		flex-shrink: 0;
	}

	.space-chip:hover {
		color: var(--color-text-primary);
		border-color: var(--color-accent);
	}

	.space-chip.active {
		background: var(--color-accent);
		color: #fff;
		border-color: var(--color-accent);
	}

	.space-chip.private.active {
		background: var(--color-space-private-bg);
		color: var(--color-space-private-text);
		border-color: var(--color-space-private-bg);
	}

	/* ── Feed ───────────────────────────────────────────────────── */
	.feed {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		padding-bottom: 5rem; /* clearance for FAB */
	}

	.card-header {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		flex-wrap: wrap;
	}

	.card-title {
		flex: 1;
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.card-title.done {
		opacity: 0.4;
		text-decoration: line-through;
	}

	.card-desc {
		margin-top: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-secondary);
		line-height: 1.5;
	}

	.card-date {
		font-size: var(--font-size-xs);
		color: var(--color-text-faint);
		white-space: nowrap;
		flex-shrink: 0;
	}

	.card-deadline {
		font-size: var(--font-size-xs);
		color: var(--color-priority-medium);
		white-space: nowrap;
		flex-shrink: 0;
	}

	/* children inside group card */
	.children {
		list-style: none;
		margin-top: var(--space-3);
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding-left: var(--space-3);
		border-left: 1px solid var(--color-border);
	}

	.child {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-base);
	}

	.child-icon {
		font-size: var(--font-size-xs);
		flex-shrink: 0;
		color: var(--color-text-faint);
	}

	.child[data-priority='high']   .child-icon { color: var(--color-priority-high); }
	.child[data-priority='medium'] .child-icon { color: var(--color-priority-medium); }
	.child[data-priority='low']    .child-icon { color: var(--color-priority-low); }
	.record-child .child-icon.rec  { color: var(--color-kind-record-border); }

	.child-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: var(--color-text-primary);
	}

	.child-name.done { opacity: 0.4; text-decoration: line-through; }

	.child-preview {
		font-size: var(--font-size-xs);
		color: var(--color-text-faint);
		flex-shrink: 0;
	}

	.child-deadline {
		font-size: var(--font-size-xs);
		color: var(--color-priority-medium);
		flex-shrink: 0;
	}

	.empty-group {
		margin-top: var(--space-2);
		font-size: var(--font-size-sm);
		color: var(--color-text-faint);
	}

	/* load more */
	.load-more {
		margin-top: var(--space-6);
		display: flex;
		justify-content: center;
		padding-bottom: 5rem;
	}

	/* ── FAB ────────────────────────────────────────────────────── */
	.fab {
		position: fixed;
		bottom: 1.75rem;
		right: 1.75rem;
		width: 56px;
		height: 56px;
		border-radius: var(--radius-pill);
		background: var(--color-accent);
		color: #fff;
		border: none;
		cursor: pointer;
		font-size: 1.75rem;
		line-height: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		box-shadow: 0 4px 16px rgba(0,0,0,0.4);
		z-index: 110;
		transition: background 0.15s;
	}

	.fab:hover { background: var(--color-accent-hover); }

	.fab-icon {
		display: block;
		transition: transform 0.2s;
		line-height: 1;
	}

	.fab-icon.open { transform: rotate(45deg); }

	/* backdrop */
	.backdrop {
		position: fixed;
		inset: 0;
		background: rgba(0,0,0,0.45);
		border: none;
		z-index: 105;
		cursor: default;
	}

	/* sheet — slides up from bottom */
	.fab-sheet {
		position: fixed;
		bottom: 0;
		left: 50%;
		transform: translateX(-50%);
		width: min(640px, 100vw);
		background: var(--color-bg-surface);
		border-top: 1px solid var(--color-border);
		border-radius: var(--radius-xl) var(--radius-xl) 0 0;
		padding: var(--space-5) var(--space-5) calc(var(--space-5) + env(safe-area-inset-bottom));
		z-index: 106;
		animation: slide-up 0.18s ease-out;
	}

	@media (max-width: 640px) {
		.fab {
			bottom: calc(3.5rem + env(safe-area-inset-bottom, 0px) + var(--space-4));
		}
		.fab-sheet {
			bottom: calc(3.5rem + env(safe-area-inset-bottom, 0px));
			padding-bottom: var(--space-5);
		}
	}

	@keyframes slide-up {
		from { transform: translateX(-50%) translateY(100%); opacity: 0; }
		to   { transform: translateX(-50%) translateY(0);    opacity: 1; }
	}

	/* menu grid */
	.fab-menu {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: var(--space-3);
	}

	.fab-option {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-4) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-bg-sunken);
		color: var(--color-text-secondary);
		cursor: pointer;
		font-size: var(--font-size-sm);
		font-family: inherit;
		transition: background 0.1s, border-color 0.1s;
	}

	.fab-option:hover { background: var(--color-bg-elevated); }

	.fab-option-icon {
		font-size: 1.4rem;
		line-height: 1;
	}

	.fab-option.task   .fab-option-icon { color: var(--color-kind-task-text); }
	.fab-option.record .fab-option-icon { color: var(--color-kind-record-text); }
	.fab-option.group  .fab-option-icon { color: var(--color-kind-group-text); }
	.fab-option.space  .fab-option-icon { color: var(--color-kind-space-text); }

	/* inline forms */
	.fab-form {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.space-nudge {
		font-size: var(--font-size-sm);
		color: var(--color-text-secondary);
		line-height: 1.5;
		margin: 0;
	}

	.space-nudge a {
		color: var(--color-accent);
		text-decoration: underline;
	}

	.form-title {
		font-size: var(--font-size-base);
		font-weight: 600;
		color: var(--color-text-primary);
		margin: 0;
	}

	.form-actions {
		display: flex;
		gap: var(--space-2);
	}
</style>
