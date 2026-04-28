import { createApp, type Component } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as ElementPlusIcons from '@element-plus/icons-vue';
import App from './App.vue';
import router from './router';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus);
for (const [iconName, iconComponent] of Object.entries(ElementPlusIcons)) {
  app.component(iconName, iconComponent as Component);
}
app.mount('#app');
