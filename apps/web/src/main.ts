import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { chargerSession } from './session'
import './style.css'

createApp(App).use(router).mount('#app')
void chargerSession()
