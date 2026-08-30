// Service Worker بسيط — مطلوب من Chrome باش يعتبر التطبيق "قابل للتثبيت"
// (يفعّل ظهور اقتراح "تثبيت التطبيق" / "إضافة للشاشة الرئيسية")

const CACHE_NAME = 'souk-beladi-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

// معالج fetch أساسي: يجيب من الشبكة، وإذا فشل (بلا نت) يرجع للنسخة المخزنة إذا موجودة
self.addEventListener('fetch', (event) => {
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});
