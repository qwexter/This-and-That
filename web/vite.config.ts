import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
	server: {
		proxy: {
			'/health': 'http://localhost:8080',
			'/me': 'http://localhost:8080',
			'/tasks': 'http://localhost:8080',
			'/records': 'http://localhost:8080',
			'/groups': 'http://localhost:8080',
			'/spaces': 'http://localhost:8080',
			'/invites': 'http://localhost:8080',
			'/feed': 'http://localhost:8080'
		}
	},
	plugins: [
		sveltekit(),
		VitePWA({
			registerType: 'autoUpdate',
			manifest: {
				name: 'This and That',
				short_name: 'TaT',
				description: 'Simple task manager',
				theme_color: '#1a1a2e',
				background_color: '#1a1a2e',
				display: 'standalone',
				start_url: '/',
				icons: [
					{ src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
					{ src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' }
				]
			},
			workbox: {
				globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
				runtimeCaching: [
					{
						urlPattern: /\/(tasks|records|groups|spaces|feed)/,
						handler: 'StaleWhileRevalidate',
						options: {
							cacheName: 'api-cache',
							cacheableResponse: { statuses: [0, 200] }
						}
					}
				]
			}
		})
	]
});
