/**
 * Auth module - Authentication logic
 */

import { authApi } from '../api/auth.api'
import { saveAccessToken, removeAccessToken, getAccessToken } from '~/shared/utils/tokenStorage'
import type { LoginDTO } from '../types/auth.dto'
import { ROUTES } from '~/shared/constants/routes'

export function useAuth() {
  const user = useState<string | null>('auth:user', () => null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  /**
   * Login
   */
  const login = async (credentials: LoginDTO) => {
    loading.value = true
    error.value = null

    try {
      const fullAuthUserDTO = await authApi.login(credentials)
      console.log('fullAuthUserDTO:', fullAuthUserDTO)
      
      // Save accessToken and expiration time
      saveAccessToken(fullAuthUserDTO.accessToken, fullAuthUserDTO.expire)
      
      // Save user info
      user.value = fullAuthUserDTO.username
      console.log('username:', fullAuthUserDTO.username)
      console.log('user.value:', user.value)

      // Redirect to admin overview
      await navigateTo(ROUTES.ADMIN.OVERVIEW)
      
      return fullAuthUserDTO
    } catch (e) {
      error.value = e as Error
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * Logout
   */
  const logout = async () => {
    loading.value = true
    error.value = null

    try {
      await authApi.logout()
    } catch (e) {
      console.error('Logout failed:', e)
    } finally {
      // Clear local data
      removeAccessToken()
      user.value = null
      loading.value = false
      
      // Redirect to login page
      await navigateTo(ROUTES.LOGIN)
    }
  }

  /**
   * Check if already logged in
   */
  const isAuthenticated = computed(() => !!user.value && !!getAccessToken())

  return {
    user: readonly(user),
    loading: readonly(loading),
    error: readonly(error),
    isAuthenticated,
    login,
    logout
  }
}
