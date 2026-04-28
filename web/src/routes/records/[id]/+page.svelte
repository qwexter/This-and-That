<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import type { Record } from '$lib/types';
	import BackLink from '$lib/ui/BackLink.svelte';
	import Button from '$lib/ui/Button.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import FormField from '$lib/ui/FormField.svelte';
	import TextInput from '$lib/ui/TextInput.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { toast } from '$lib/ui/toast.svelte';
	import { connection } from '$lib/connection.svelte';

	const id = $derived($page.params.id!);

	let record = $state<Record | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let saving = $state(false);

	let editTitle = $state('');
	let editContent = $state('');

	function applyRecord(r: typeof record & NonNullable<unknown>) {
		record = r;
		editTitle = r.title;
		editContent = r.content ?? '';
		loading = false;
	}

	async function load() {
		try {
			await api.getRecord(id, applyRecord, applyRecord);
		} catch (e) {
			error = (e as Error).message;
			loading = false;
		}
	}

	async function save() {
		if (!editTitle.trim()) return;
		saving = true;
		try {
			const updated = await api.updateRecord(id, {
				title: editTitle.trim(),
				content: editContent || null
			});
			if (updated) record = updated;
			toast.success('Record saved');
		} catch (e) {
			toast.error((e as Error).message);
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function remove() {
		await api.deleteRecord(id);
		toast.success('Record deleted');
		goto('/records');
	}

	$effect(() => { load(); });
	$effect(() => { return connection.onReconnect(() => { load(); }); });
</script>

<BackLink href="/records" label="Records" />

{#if loading}
	<EmptyState variant="page">Loading…</EmptyState>
{:else if error}
	<EmptyState variant="error">{error}</EmptyState>
{:else if record}
	<form class="form" onsubmit={(e) => { e.preventDefault(); save(); }}>
		<FormField label="Title" id="title">
			<TextInput id="title" bind:value={editTitle} maxlength={200} />
		</FormField>

		<FormField label="Content" id="content">
			<Textarea id="content" bind:value={editContent} rows={12} maxlength={5000} showCounter />
		</FormField>

		<div class="actions">
			<Button type="submit" variant="primary" disabled={saving}>
				{saving ? 'Saving…' : 'Save'}
			</Button>
			<Button type="button" variant="danger" onclick={remove}>Delete</Button>
		</div>
	</form>
{/if}

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.actions {
		display: flex;
		gap: var(--space-3);
		margin-top: var(--space-2);
	}
</style>
