<script lang="ts">
	import { api } from '$lib/api';
	import type { Me, Space, Task, Group } from '$lib/types';
	import Badge from '$lib/ui/Badge.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import SectionHeading from '$lib/ui/SectionHeading.svelte';
	import { connection } from '$lib/connection.svelte';

	let me = $state<Me | null>(null);
	let spaces = $state<Space[]>([]);
	let tasks = $state<Task[]>([]);
	let groups = $state<Group[]>([]);
	let recordCount = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);

	const tasksDone = $derived(tasks.filter(t => t.status === 'Done').length);
	const tasksTodo = $derived(tasks.filter(t => t.status === 'Todo').length);
	const ownedSpaces = $derived(spaces.filter(s => !s.isPrivate && me && s.ownerId === me.userId));
	const memberSpaces = $derived(spaces.filter(s => !s.isPrivate && me && s.ownerId !== me.userId));

	async function load() {
		try {
			await Promise.all([
				api.getMe().then(v => { me = v; }),
				api.getSpaces(
					(cached) => { spaces = cached; },
					(fresh)  => { spaces = fresh; }
				),
				api.getTasks(
					(cached) => { tasks = cached; },
					(fresh)  => { tasks = fresh; }
				),
				api.getGroups(
					(cached) => { groups = cached; },
					(fresh)  => { groups = fresh; }
				),
				api.getRecords(
					(cached) => { recordCount = cached.length; },
					(fresh)  => { recordCount = fresh.length; }
				),
			]);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if me}
	<div class="profile">

		<!-- Identity -->
		<section class="section identity">
			<div class="avatar" aria-hidden="true">{me.displayName[0].toUpperCase()}</div>
			<div class="id-info">
				<h1 class="display-name">{me.displayName}</h1>
				<span class="user-id">@{me.userId}</span>
			</div>
		</section>

		<!-- Stats -->
		<section class="section">
			<SectionHeading>Stats</SectionHeading>
			<div class="stats-grid">
				<Card compact>
					<div class="stat">
						<span class="stat-value">{tasksTodo}</span>
						<span class="stat-label">open tasks</span>
					</div>
				</Card>
				<Card compact>
					<div class="stat">
						<span class="stat-value">{tasksDone}</span>
						<span class="stat-label">done tasks</span>
					</div>
				</Card>
				<Card compact>
					<div class="stat">
						<span class="stat-value">{recordCount}</span>
						<span class="stat-label">records</span>
					</div>
				</Card>
				<Card compact>
					<div class="stat">
						<span class="stat-value">{groups.length}</span>
						<span class="stat-label">groups</span>
					</div>
				</Card>
			</div>
		</section>

		<!-- Spaces owned -->
		{#if ownedSpaces.length > 0}
			<section class="section">
				<SectionHeading>Spaces you own</SectionHeading>
				<ul class="space-list">
					{#each ownedSpaces as space (space.id)}
						<li>
							<Card compact>
								<div class="space-row">
									<a href="/spaces/{space.id}" class="space-title">{space.title}</a>
									<Badge variant="space-shared" pill>owner</Badge>
								</div>
							</Card>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		<!-- Spaces member of -->
		{#if memberSpaces.length > 0}
			<section class="section">
				<SectionHeading>Spaces you're in</SectionHeading>
				<ul class="space-list">
					{#each memberSpaces as space (space.id)}
						<li>
							<Card compact>
								<div class="space-row">
									<a href="/spaces/{space.id}" class="space-title">{space.title}</a>
									<Badge variant="space-private" pill>member</Badge>
								</div>
							</Card>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		<!-- Auth info -->
		<section class="section">
			<SectionHeading>Session</SectionHeading>
			<Card compact>
				<dl class="info-list">
					<dt>User ID</dt>
					<dd><code>{me.userId}</code></dd>
					<dt>Auth</dt>
					<dd>Managed by Authelia — no password change here</dd>
				</dl>
			</Card>
		</section>

	</div>
{/if}

<style>
	.profile {
		display: flex;
		flex-direction: column;
		gap: var(--space-8);
	}

	.section {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	/* Identity */
	.identity {
		flex-direction: row;
		align-items: center;
		gap: var(--space-4);
	}

	.avatar {
		width: 56px;
		height: 56px;
		border-radius: var(--radius-pill);
		background: var(--color-accent);
		color: #fff;
		font-size: 1.5rem;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.id-info {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.display-name {
		font-size: var(--font-size-lg, 1.25rem);
		font-weight: 700;
		color: var(--color-text-primary);
		margin: 0;
	}

	.user-id {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		font-family: monospace;
	}

	/* Stats */
	.stats-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: var(--space-2);
	}

	@media (max-width: 400px) {
		.stats-grid { grid-template-columns: repeat(2, 1fr); }
	}

	.stat {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-2) 0;
	}

	.stat-value {
		font-size: 1.5rem;
		font-weight: 700;
		color: var(--color-text-primary);
		line-height: 1;
	}

	.stat-label {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		text-align: center;
	}

	/* Space list */
	.space-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.space-row {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.space-title {
		flex: 1;
		font-size: var(--font-size-base);
		font-weight: 500;
		color: var(--color-text-primary);
	}

	/* Auth info */
	.info-list {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--space-2) var(--space-4);
		align-items: baseline;
	}

	dt {
		font-size: var(--font-size-xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
		white-space: nowrap;
	}

	dd {
		font-size: var(--font-size-sm);
		color: var(--color-text-secondary);
	}

	code {
		font-family: monospace;
		font-size: 0.85em;
		background: var(--color-bg-sunken);
		padding: 0.1em 0.4em;
		border-radius: var(--radius-sm, 3px);
	}
</style>
