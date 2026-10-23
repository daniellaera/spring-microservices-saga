// Short commit SHA of the build: `ng build --define 'SHOP_UI_BUILD_SHA="<sha>"'` (the Dockerfile's
// BUILD_SHA build arg, set by main.yml). Without it, the badge says "dev".
declare const SHOP_UI_BUILD_SHA: string | undefined;

export const environment = {
  production: true,
  apiUrl: '',
  version: 'prod',
  buildNumber: typeof SHOP_UI_BUILD_SHA === 'string' ? SHOP_UI_BUILD_SHA : 'dev',
  showDemoCredentials: false
};
