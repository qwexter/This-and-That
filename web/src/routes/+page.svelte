<script lang="ts">
	import { api } from '$lib/api';
	import type { FeedEntry, FeedPage } from '$lib/types';

	function formatDate(iso: string): string {
		const d = new Date(iso);
		const now = new Date();
		const diff = now.getTime() - d.getTime();
		if (diff < 60_000) return 'just now';
		if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
		if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
		return `${Math.floor(diff / 86_400_000)}d ago`;
	}

	const LIMIT = 20;

	let page = $state<FeedPage | null>(null);
	let loading = $state(true);
	let loadingMore = $state(false);
	let error = $state<string | null>(null);
	let items = $state<FeedEntry[]>([]);

	async function load(offset = 0) {
		try {
			const result = await api.getFeed({ limit: LIMIT, offset });
			if (offset === 0) {
				items = result.items;
			} else {
				items = [...items, ...result.items];
			}
			page = result;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function loadMore() {
		if (!page) return;
		loadingMore = true;
		try {
			await load(items.length);
		} finally {
			loadingMore = false;
		}
	}

	const hasMore = $derived(page != null && items.length < page.total);

	$effect(() => {
		load().finally(() => { loading = false; });
	});
</script>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if items.length === 0}
	<p class="state">No items yet. Create a task, record, or group.</p>
{:else}
	<div class="feed">
		{#each items as entry (entry.kind + entry.id)}
			{#if entry.kind === 'group'}
				<div class="feed-card group-card">
					<div class="card-header">
						<span class="kind-badge kind-group">group</span>
						<a href="/groups/{entry.id}" class="card-title">{entry.title}</a>
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if entry.children.length > 0}
						<ul class="children">
							{#each entry.children as child (child.kind + child.id)}
								{#if child.kind === 'task'}
									<li class="child" data-priority={child.priority.toLowerCase()}>
										<span class="child-icon">✓</span>
										<a href="/tasks/{child.id}" class="child-name" class:done={child.status === 'Done'}>{child.name}</a>
										<span class="child-badge">{child.priority}</span>
										<span class="child-status" class:done={child.status === 'Done'}>{child.status}</span>
									</li>
								{:else}
									<li class="child record-child">
										<span class="child-icon">≡</span>
										<a href="/records/{child.id}" class="child-name">{child.title}</a>
										{#if child.content}
											<span class="child-preview">{child.content.slice(0, 60)}{child.content.length > 60 ? '…' : ''}</span>
										{/if}
									</li>
								{/if}
							{/each}
						</ul>
					{:else}
						<p class="empty-group">No items in this group.</p>
					{/if}
				</div>
			{:else if entry.kind === 'task'}
				<div class="feed-card task-card" data-priority={entry.priority.toLowerCase()}>
					<div class="card-header">
						<span class="kind-badge kind-task">task</span>
						<a href="/tasks/{entry.id}" class="card-title" class:done={entry.status === 'Done'}>{entry.name}</a>
						<span class="badge">{entry.priority}</span>
						<span class="status-badge" class:done={entry.status === 'Done'}>{entry.status}</span>
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if entry.description}
						<p class="card-desc">{entry.description}</p>
					{/if}
				</div>
			{:else}
				<div class="feed-card record-card">
					<div class="card-header">
						<span class="kind-badge kind-record">record</span>
						<a href="/records/{entry.id}" class="card-title">{entry.title}</a>
						<span class="card-date">{formatDate(entry.createdAt)}</span>
					</div>
					{#if entry.content}
						<p class="card-desc">{entry.content.slice(0, 120)}{entry.content.length > 120 ? '…' : ''}</p>
					{/if}
				</div>
			{/if}
		{/each}
	</div>

	{#if hasMore}
		<div class="load-more">
			<button onclick={loadMore} disabled={loadingMore}>
				{loadingMore ? 'Loading…' : `Load more (${page!.total - items.length} remaining)`}
			</button>
		</div>
	{/if}
{/if}

<style>
	.state {
		text-align: center;
		color: #888;
		padding: 2rem 0;
	}
	.state.error { color: #f87171; }

	.feed {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.feed-card {
		padding: 0.875rem 1rem;
		background: #16213e;
		border-radius: 8px;
		border-left: 3px solid transparent;
	}

	.task-card[data-priority='high'] { border-left-color: #f87171; }
	.task-card[data-priority='medium'] { border-left-color: #fbbf24; }
	.task-card[data-priority='low'] { border-left-color: #34d399; }
	.group-card { border-left-color: #818cf8; }
	.record-card { border-left-color: #38bdf8; }

	.card-header {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		flex-wrap: wrap;
	}

	.card-title {
		flex: 1;
		font-size: 0.95rem;
		font-weight: 500;
	}

	.card-title.done {
		opacity: 0.4;
		text-decoration: line-through;
	}

	.card-desc {
		margin-top: 0.4rem;
		font-size: 0.82rem;
		color: #9ca3af;
		line-height: 1.4;
	}

	.card-date {
		font-size: 0.72rem;
		color: #555;
		white-space: nowrap;
	}

	.kind-badge {
		font-size: 0.65rem;
		padding: 0.1rem 0.35rem;
		border-radius: 3px;
		font-weight: 600;
		letter-spacing: 0.03em;
		text-transform: uppercase;
	}

	.kind-group { background: #312e81; color: #a5b4fc; }
	.kind-task { background: #1e3a5f; color: #7dd3fc; }
	.kind-record { background: #0f4c75; color: #bae6fd; }

	.badge {
		font-size: 0.7rem;
		padding: 0.1rem 0.4rem;
		background: #2a2a4a;
		border-radius: 4px;
		color: #aaa;
	}

	.status-badge {
		font-size: 0.7rem;
		padding: 0.1rem 0.4rem;
		background: #2a2a4a;
		border-radius: 4px;
		color: #34d399;
	}

	.status-badge.done { color: #888; }

	.children {
		list-style: none;
		margin-top: 0.6rem;
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		padding-left: 0.5rem;
		border-left: 1px solid #2a2a4a;
	}

	.child {
		display: flex;
		align-items: center;
		gap: 0.4rem;
		font-size: 0.85rem;
	}

	.child[data-priority='high'] .child-icon { color: #f87171; }
	.child[data-priority='medium'] .child-icon { color: #fbbf24; }
	.child[data-priority='low'] .child-icon { color: #34d399; }
	.record-child .child-icon { color: #38bdf8; }

	.child-icon { font-size: 0.75rem; flex-shrink: 0; }

	.child-name { flex: 1; }
	.child-name.done { opacity: 0.4; text-decoration: line-through; }

	.child-badge {
		font-size: 0.65rem;
		padding: 0.1rem 0.3rem;
		background: #2a2a4a;
		border-radius: 3px;
		color: #aaa;
	}

	.child-status { font-size: 0.65rem; color: #555; }
	.child-status.done { color: #444; }

	.child-preview {
		font-size: 0.72rem;
		color: #666;
		flex: 1;
	}

	.empty-group {
		margin-top: 0.5rem;
		font-size: 0.8rem;
		color: #555;
	}

	.load-more {
		margin-top: 1.5rem;
		text-align: center;
	}

	.load-more button {
		padding: 0.5rem 1.5rem;
		background: #2a2a4a;
		border: none;
		border-radius: 6px;
		color: #aaa;
		cursor: pointer;
		font-size: 0.9rem;
	}

	.load-more button:hover:not(:disabled) { background: #3a3a6a; color: #e2e2e2; }
	.load-more button:disabled { opacity: 0.4; cursor: default; }
</style>
