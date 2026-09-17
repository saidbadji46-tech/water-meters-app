import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite } from '@capacitor-community/sqlite';

const DB_NAME = 'adeMeterData';
const DB_VERSION = 1;
let readyPromise = null;
let nativeMode = false;

function isNative() {
  try {
    return !!Capacitor && Capacitor.isNativePlatform();
  } catch (_) {
    return false;
  }
}

function openIndexedDBRaw() {
  return new Promise((resolve, reject) => {
    try {
      const req = indexedDB.open('meterReadingAppDB', 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains('kv')) req.result.createObjectStore('kv');
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    } catch (e) { reject(e); }
  });
}

async function readOldIndexedDB() {
  const db = await openIndexedDBRaw();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('kv', 'readonly');
    const store = tx.objectStore('kv');
    const req = store.getAll();
    const keysReq = store.getAllKeys();
    let values, keys;
    req.onsuccess = () => { values = req.result || []; if (keys !== undefined) resolve({keys, values}); };
    keysReq.onsuccess = () => { keys = keysReq.result || []; if (values !== undefined) resolve({keys, values}); };
    tx.onerror = () => reject(tx.error);
  });
}

async function ensureNativeDatabase() {
  if (!isNative()) return false;
  nativeMode = true;
  try {
    const exists = await CapacitorSQLite.isConnection({ database: DB_NAME, readonly: false });
    if (!exists.result) {
      await CapacitorSQLite.createConnection({
        database: DB_NAME,
        encrypted: false,
        mode: 'no-encryption',
        version: DB_VERSION,
        readonly: false
      });
    }
  } catch (e) {
    // If the connection already exists, retrieve/open it below.
    try {
      await CapacitorSQLite.retrieveConnection({ database: DB_NAME, readonly: false });
    } catch (_) { throw e; }
  }
  await CapacitorSQLite.open({ database: DB_NAME, readonly: false });
  await CapacitorSQLite.execute({
    database: DB_NAME,
    statements: `CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY NOT NULL, value TEXT);`,
    transaction: true,
    readonly: false
  });
  return true;
}

async function nativeGet(key) {
  const res = await CapacitorSQLite.query({
    database: DB_NAME,
    statement: 'SELECT value FROM kv WHERE key = ? LIMIT 1',
    values: [String(key)],
    readonly: false
  });
  const rows = res && Array.isArray(res.values) ? res.values : [];
  return rows.length ? rows[0].value : null;
}

async function nativeSet(key, value) {
  await CapacitorSQLite.run({
    database: DB_NAME,
    statement: 'INSERT OR REPLACE INTO kv(key,value) VALUES(?,?)',
    values: [String(key), String(value)],
    transaction: true,
    readonly: false,
    returnMode: 'no'
  });
  return true;
}

async function nativeDelete(key) {
  await CapacitorSQLite.run({
    database: DB_NAME,
    statement: 'DELETE FROM kv WHERE key = ?',
    values: [String(key)],
    transaction: true,
    readonly: false,
    returnMode: 'no'
  });
}

async function migrateFromIndexedDB() {
  const migrated = await nativeGet('__idb_migrated_v1');
  if (migrated === '1') return;
  try {
    const old = await readOldIndexedDB();
    for (let i = 0; i < old.keys.length; i++) {
      const key = String(old.keys[i]);
      const value = old.values[i];
      if (value !== undefined && value !== null) {
        await nativeSet(key, typeof value === 'string' ? value : JSON.stringify(value));
      }
    }
  } catch (_) {
    // No old database is normal on a fresh installation.
  }
  await nativeSet('__idb_migrated_v1', '1');
}

readyPromise = (async () => {
  if (!isNative()) return false;
  await ensureNativeDatabase();
  await migrateFromIndexedDB();
  return true;
})().catch((e) => {
  console.error('Native SQLite initialization failed:', e);
  nativeMode = false;
  return false;
});

window.__nativeSQLiteReady = readyPromise;
window.__nativeSQLite = {
  isNative: () => nativeMode,
  ready: () => readyPromise,
  get: async (key) => { await readyPromise; return nativeMode ? nativeGet(key) : null; },
  set: async (key, value) => { await readyPromise; return nativeMode ? nativeSet(key, value) : false; },
  delete: async (key) => { await readyPromise; if (nativeMode) await nativeDelete(key); }
};
