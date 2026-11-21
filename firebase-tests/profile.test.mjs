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

test('el saldo tiene una única ruta, privada e inmutable', async () => {
  const owner = env.authenticatedContext('alice').firestore();
  const path = 'usuarios/alice/efectivo/saldoInicial';
  const balance = { centimos: -1250, dia: 19723 };
  const reference = doc(owner, path);
  for (const outsider of [env.authenticatedContext('bob'), env.unauthenticatedContext()]) {
    const foreignReference = doc(outsider.firestore(), path);
    await assertFails(setDoc(foreignReference, balance));
  }
  await assertSucceeds(setDoc(reference, balance));
  assert.deepEqual((await assertSucceeds(getDoc(reference))).data(), balance);
  for (const outsider of [env.authenticatedContext('bob'), env.unauthenticatedContext()]) {
    await assertFails(getDoc(doc(outsider.firestore(), path)));
  }
  await assertFails(setDoc(reference, { ...balance, centimos: 0 }));
  await assertFails(deleteDoc(reference));
  await assertFails(setDoc(doc(owner, 'usuarios/alice/efectivo/segundoSaldo'), balance));
});

test('las reglas exigen céntimos enteros y un día de calendario válido', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const reference = doc(db, 'usuarios/alice/efectivo/saldoInicial');
  const valid = { centimos: 0, dia: 19723 };
  const future = Math.floor(Date.now() / 86400000) + 2;
  for (const data of [{ dia: valid.dia }, { centimos: 0 }, { ...valid, centimos: 0.01 },
    { ...valid, centimos: '0' }, { ...valid, dia: 19723.5 }, { ...valid, dia: future },
    { ...valid, dia: -719163 }, { ...valid, moneda: 'USD' }]) {
    await assertFails(setDoc(reference, data));
  }
  await assertSucceeds(setDoc(reference, valid));
});
