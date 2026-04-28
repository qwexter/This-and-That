<script lang="ts">
	import '$lib/ui/tokens.css';
	import Toaster from '$lib/ui/Toaster.svelte';
	import OfflineBanner from '$lib/ui/OfflineBanner.svelte';
	import { page } from '$app/stores';
	import { initSync } from '$lib/sync.svelte';
	import { initConnection } from '$lib/connection.svelte';

	$effect(() => { initSync(); initConnection(); });

	let { children } = $props();

	type Theme = 'dark' | 'light';

	function systemTheme(): Theme {
		if (typeof window === 'undefined') return 'dark';
		return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
	}

	let theme = $state<Theme>(systemTheme());

	$effect(() => {
		const mq = window.matchMedia('(prefers-color-scheme: light)');
		function onChange(e: MediaQueryListEvent) {
			theme = e.matches ? 'light' : 'dark';
		}
		mq.addEventListener('change', onChange);
		return () => mq.removeEventListener('change', onChange);
	});

	$effect(() => {
		document.documentElement.dataset.theme = theme;
	});

	function toggleTheme() {
		theme = theme === 'dark' ? 'light' : 'dark';
	}
</script>

<svelte:head>
	<meta name="theme-color" content={theme === 'light' ? '#f0f0f7' : '#1a1a2e'} />
</svelte:head>

<div class="theme-root">
	<div class="app">
		<header>
			<a href="/" class="brand">TaT</a>
			<nav class="top-nav">
				<a href="/">Feed</a>
				<a href="/tasks">Tasks</a>
				<a href="/records">Records</a>
				<a href="/groups">Groups</a>
				<a href="/spaces">Spaces</a>
			</nav>
			<button class="theme-toggle" onclick={toggleTheme} aria-label="Toggle theme">
				{theme === 'dark' ? '☀' : '☾'}
			</button>
		</header>
		<main>
			{@render children()}
		</main>
	</div>
	<OfflineBanner />
	<Toaster />
	<nav class="bottom-nav" aria-label="Main navigation">
		<a href="/" class:active={$page.url.pathname === '/'}>
			<span class="nav-icon">⊞</span>
			<span class="nav-label">Feed</span>
		</a>
		<a href="/tasks" class:active={$page.url.pathname.startsWith('/tasks')}>
			<span class="nav-icon">✓</span>
			<span class="nav-label">Tasks</span>
		</a>
		<a href="/records" class:active={$page.url.pathname.startsWith('/records')}>
			<span class="nav-icon">◧</span>
			<span class="nav-label">Records</span>
		</a>
		<a href="/groups" class:active={$page.url.pathname.startsWith('/groups')}>
			<span class="nav-icon">⊟</span>
			<span class="nav-label">Groups</span>
		</a>
		<a href="/spaces" class:active={$page.url.pathname.startsWith('/spaces')}>
			<span class="nav-icon">◈</span>
			<span class="nav-label">Spaces</span>
		</a>
	</nav>
</div>

<style>
	:global(*, *::before, *::after) {
		box-sizing: border-box;
		margin: 0;
		padding: 0;
	}

	:global(a) {
		color: inherit;
		text-decoration: none;
	}

	.theme-root {
		min-height: 100dvh;
		background: var(--color-bg-page);
		color: var(--color-text-primary);
		font-family: system-ui, sans-serif;
	}

	.app {
		max-width: 640px;
		margin: 0 auto;
		padding: 0 var(--space-4);
	}

	header {
		display: flex;
		align-items: center;
		gap: var(--space-6);
		padding: var(--space-4) 0;
		border-bottom: 1px solid var(--color-border);
	}

	.brand {
		font-size: 1.25rem;
		font-weight: 700;
		letter-spacing: 0.05em;
		color: var(--color-text-primary);
	}

	nav.top-nav {
		display: flex;
		gap: var(--space-4);
		flex: 1;
	}

	nav.top-nav a {
		font-size: var(--font-size-base);
		color: var(--color-text-muted);
	}

	nav.top-nav a:hover {
		color: var(--color-text-primary);
	}

	nav.bottom-nav {
		display: none;
		position: fixed;
		bottom: 0;
		left: 0;
		right: 0;
		background: var(--color-bg-surface);
		border-top: 1px solid var(--color-border);
		padding-bottom: env(safe-area-inset-bottom, 0);
		z-index: 100;
	}

	nav.bottom-nav a {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		padding: var(--space-2) 0;
		color: var(--color-text-muted);
		flex: 1;
		font-size: var(--font-size-xs);
		transition: color 0.15s;
	}

	nav.bottom-nav a.active,
	nav.bottom-nav a:hover {
		color: var(--color-accent);
	}

	.nav-icon {
		font-size: 1.1rem;
		line-height: 1;
	}

	.nav-label {
		font-size: 0.65rem;
		font-weight: 500;
		letter-spacing: 0.02em;
	}

	@media (max-width: 640px) {
		nav.top-nav { display: none; }
		nav.bottom-nav { display: flex; }
		main { padding-bottom: calc(var(--space-6) + 3.5rem + env(safe-area-inset-bottom, 0px)); }
	}

	.theme-toggle {
		background: transparent;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		color: var(--color-text-muted);
		cursor: pointer;
		padding: 0.25rem 0.5rem;
		font-size: var(--font-size-base);
		line-height: 1;
		flex-shrink: 0;
	}

	.theme-toggle:hover {
		color: var(--color-text-primary);
		border-color: var(--color-accent);
	}

	main {
		padding: var(--space-6) 0;
	}
</style>
