import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';
import { readFile } from 'node:fs/promises';

let env;
/** Carga las reglas reales contra un proyecto demo aislado del Firebase remoto. */
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-vesta',
    firestore: { rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
beforeEach(async () => env.clearFirestore());
after(async () => env?.cleanup());

/** Demuestra acceso propio y denegación de lectura/escritura cruzada y anónima. */
test('cada usuario conserva su perfil privado', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const guest = env.unauthenticatedContext().firestore();
  await assertSucceeds(setDoc(doc(alice, 'usuarios/alice'), { nombreCompleto: 'Alicia' }));
  await assertSucceeds(setDoc(doc(bob, 'usuarios/bob'), { nombreCompleto: 'Roberto' }));
  assert.equal((await assertSucceeds(getDoc(doc(alice, 'usuarios/alice')))).data().nombreCompleto, 'Alicia');
  for (const outsider of [bob, guest]) {
    await assertFails(getDoc(doc(outsider, 'usuarios/alice')));
    await assertFails(setDoc(doc(outsider, 'usuarios/alice'), { nombreCompleto: 'Intruso' }));
  }
  await assertFails(getDocs(collection(alice, 'usuarios')));
  await assertFails(deleteDoc(doc(alice, 'usuarios/alice')));
});

/** La validación remota rechaza tipos erróneos, nombres vacíos y campos secretos. */
test('el perfil solo acepta un nombre válido', async () => {
  const db = env.authenticatedContext('alice').firestore();
  for (const data of [{}, { nombreCompleto: '' }, { nombreCompleto: '  \t' }, { nombreCompleto: 42 },
    { nombreCompleto: 'x'.repeat(101) }, { nombreCompleto: 'Alicia', password: 'secreto' }]) {
    await assertFails(setDoc(doc(db, 'usuarios/alice'), data));
  }
  await assertSucceeds(setDoc(doc(db, 'usuarios/alice'), { nombreCompleto: 'A'.repeat(100) }));
  await assertSucceeds(setDoc(doc(db, 'usuarios/alice'), { nombreCompleto: 'Alicia corregida' }));
  await assertFails(setDoc(doc(db, 'movimientos/cualquiera'), { importe: 1 }));
});
