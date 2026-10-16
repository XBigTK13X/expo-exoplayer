import { registerWebModule, NativeModule } from 'expo';

// ExpoExoPlayerModule is not available on the web platform.
class ExpoExoPlayerModule extends NativeModule<{}> {}

export default registerWebModule(ExpoExoPlayerModule, 'ExpoExoPlayerModule');
