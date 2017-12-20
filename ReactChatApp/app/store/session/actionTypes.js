// SESSION_RESTORING ‒ restores user sessions. Firebase restores user sessions asynchronously, so the system will display a preloader until the action is completed.
// SESSION_LOADING ‒ displays a preloader until the login or registration request has been processed by Firebase.
// SESSION_SUCCESS ‒ transfers a user to the chat screen after login has successfully completed
// SESSION_ERROR ‒ displays login or registration errors
// SESSION_LOGOUT ‒ displays the login screen

export const SESSION_RESTORING = 'SESSION_RESTORING'
export const SESSION_LOADING = 'SESSION_LOADING'
export const SESSION_SUCCESS = 'SESSION_SUCCESS'
export const SESSION_ERROR = 'SESSION_ERROR'
export const SESSION_LOGOUT = 'SESSION_LOGOUT'
