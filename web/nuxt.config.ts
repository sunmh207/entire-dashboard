// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2026-03-07',
    ssr: false,
    devtools: {enabled: true},
    css: [
        '~/assets/css/main.css',
        '~/assets/css/markdown.css',
    ],
    icon: {
        provider: 'none',
        clientBundle: {
            icons:[
                'lucide:chevrons-left',
                'lucide:chevron-left',
                'lucide:chevrons-right',
                'lucide:chevron-right',
                'lucide:panel-left-close',
                'lucide:panel-left-open',
            ],
            // scan all components in the project and include icons
            scan: true,
            // include all custom collections in the client bundle
            includeCustomCollections: true,
            // guard for uncompressed bundle size, will fail the build if exceeds
            sizeLimitKb: 256,
        },
    },
    modules: [
        '@nuxt/eslint',
        '@nuxt/scripts',
        '@nuxt/test-utils',
        '@nuxt/ui',
        '@pinia/nuxt',
        '@vueuse/nuxt',
        '@nuxtjs/mdc',
    ],

    imports: {
        dirs: [
            'features/*/composables',
            'features/*/types',
            'shared/composables',
            'shared/types',
        ],
    },

    components: [
        { path: '~/features', pathPrefix: false, extensions: ['.vue'] },
        { path: '~/shared/components', pathPrefix: false },
    ],
    ui: {
        fonts: false,
        colorMode: true,
    },

    mdc: {
        highlight: {
            theme: {
                default: 'github-light',
                dark: 'github-dark',
            },
            langs: [
                'javascript',
                'typescript',
                'vue',
                'html',
                'css',
                'json',
                'bash',
                'shell',
                'python',
                'java',
                'go',
                'rust',
                'sql',
                'yaml',
                'markdown',
            ],
        },
    },

    devServer: {
        port: 3001,
    },

    runtimeConfig: {
        public: {
            apiBaseUrl: '/api/v1',
        },
    },

    nitro: {
        routeRules: {
            '/api/v1/**':
                {
                    proxy:
                        {
                            to: 'http://127.0.0.1:8080/api/v1/**',
                        },
                },
        },
    },

    colorMode: {
        preference: 'system',
        fallback: 'light',
    },
})

