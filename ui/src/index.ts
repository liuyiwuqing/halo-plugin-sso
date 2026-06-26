import { definePlugin } from '@halo-dev/ui-shared'
import { IconPlug } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/sso',
        name: 'SsoConsole',
        component: () => import(/* webpackChunkName: "HomeView" */ './views/HomeView.vue'),
        meta: {
          title: '统一身份认证',
          searchable: true,
          permissions: ['plugin:sso:console'],
          menu: {
            name: '统一身份认证',
            group: '系统工具',
            icon: markRaw(IconPlug),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
