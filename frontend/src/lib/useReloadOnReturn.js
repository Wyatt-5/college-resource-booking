import { onActivated } from 'vue'

// KeepAlive does not mount a page again when a saved tab is reopened.
export function useReloadOnReturn(reload) {
  let initialActivation = true
  onActivated(() => {
    if (initialActivation) initialActivation = false
    else reload()
  })
}
