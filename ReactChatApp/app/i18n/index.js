import I18n from 'react-native-i18n'

import en from './locales/en'

I18n.fallbacks = true

I18n.translations = {
  en,
}

export default I18n

// export default {
//   login: 'Login',
//   signup: 'Sign Up',
//
//   email: 'Email',
//   password: 'Password',
//
//   chat: 'Chat',
//   message: 'Message',
//
//   error: 'Error',
//
//   you: 'You',
//
//   placeholder: 'There are no messages yet',
// }
