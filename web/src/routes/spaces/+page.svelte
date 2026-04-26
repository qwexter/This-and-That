<script lang="ts">
	import { api } from '$lib/api';
	import type { Space } from '$lib/types';

	let spaces = $state<Space[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let newTitle = $state('');
	let adding = $state(false);

	async function load() {
		try {
			spaces = await api.getSpaces();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function addSpace() {
		if (!newTitle.trim()) return;
		adding = true;
		try {
			const space = await api.createSpace({ title: newTitle.trim() });
			spaces = [...spaces, space];
			newTitle = '';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			adding = false;
		}
	}

	async function remove(id: string) {
		await api.deleteSpace(id);
		spaces = spaces.filter((s) => s.id !== id);
	}

	$effect(() => { load(); });
</script>

<section class="add-form">
	<input
		bind:value={newTitle}
		placeholder="New space title…"
		maxlength="200"
		onkeydown={(e) => e.key === 'Enter' && addSpace()}
	/>
	<button onclick={addSpace} disabled={adding || !newTitle.trim()}>Add</button>
</section>

{#if loading}
	<p class="state">Loading…</p>
{:else if error}
	<p class="state error">{error}</p>
{:else if spaces.length === 0}
	<p class="state">No spaces yet.</p>
{:else}
	<ul class="space-list">
		{#each spaces as space (space.id)}
			<li class="space-item">
				<a href="/spaces/{space.id}" class="space-title">{space.title}</a>
				{#if space.isPrivate}
					<span class="badge">Private</span>
				{:else}
					<button class="del" onclick={() => remove(space.id)} aria-label="Delete">×</button>
				{/if}
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

	.space-list {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.space-item {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.75rem 1rem;
		background: #16213e;
		border-radius: 8px;
		border-left: 3px solid #34d399;
	}

	.space-title {
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

	.badge {
		font-size: 0.75rem;
		padding: 0.15rem 0.5rem;
		background: #2a2a4a;
		border-radius: 999px;
		color: #888;
	}
</style>
