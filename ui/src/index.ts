import { definePlugin } from '@halo-dev/ui-shared'
import SsoConnectIcon from '~icons/ri/shield-keyhole-line'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/sso',
        name: 'SsoConsole',
        component: () => import('./views/SsoConsole.vue'),
        meta: {
          title: '统一身份认证',
          searchable: true,
          permissions: ['plugin:sso:console'],
          menu: {
            name: '统一身份认证',
            group: '系统工具',
            icon: markRaw(SsoConnectIcon),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
