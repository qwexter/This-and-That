<script lang="ts">
	let { children } = $props();
</script>

<svelte:head>
	<meta name="theme-color" content="#1a1a2e" />
</svelte:head>

<div class="app">
	<header>
		<a href="/">TaT</a>
	</header>
	<main>
		{@render children()}
	</main>
</div>

<style>
	:global(*, *::before, *::after) {
		box-sizing: border-box;
		margin: 0;
		padding: 0;
	}

	:global(body) {
		font-family: system-ui, sans-serif;
		background: #1a1a2e;
		color: #e2e2e2;
		min-height: 100dvh;
	}

	:global(a) {
		color: inherit;
		text-decoration: none;
	}

	.app {
		max-width: 640px;
		margin: 0 auto;
		padding: 0 1rem;
	}

	header {
		padding: 1rem 0;
		font-size: 1.25rem;
		font-weight: 700;
		letter-spacing: 0.05em;
		border-bottom: 1px solid #2a2a4a;
	}

	main {
		padding: 1.5rem 0;
	}
</style>
