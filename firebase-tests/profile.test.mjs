import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs, runTransaction } from 'firebase/firestore';
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

test('los movimientos respetan el saldo inicial, los datos válidos y la privacidad', async () => {
  const owner = env.authenticatedContext('alice').firestore();
  const path = 'usuarios/alice/movimientos';
  const id = 'bf101add-e1d3-4acb-8202-044350add4c3';
  const reference = doc(owner, path, id);
  const today = Math.floor(Date.now() / 86400000);
  const valid = { centimos: 50000, dia: today - 1, categoria: 'INCOME', nota: '' };
  await assertFails(setDoc(reference, valid));
  await assertSucceeds(setDoc(doc(owner, 'usuarios/alice/efectivo/saldoInicial'), {
    centimos: 100000, dia: valid.dia,
  }));
  for (const data of [{ ...valid, centimos: 0 }, { ...valid, centimos: 0.01 },
    { ...valid, dia: valid.dia - 1 }, { ...valid, dia: today + 2 },
    { ...valid, categoria: 'INVALID' }, { ...valid, nota: 'x'.repeat(501) }]) {
    await assertFails(setDoc(reference, data));
  }
  for (const outsider of [env.authenticatedContext('bob'), env.unauthenticatedContext()]) {
    const db = outsider.firestore();
    await assertFails(setDoc(doc(db, path, id), valid));
    await assertFails(getDocs(collection(db, path)));
    await assertFails(getDoc(doc(db, path, id)));
  }
  const createOnce = () => runTransaction(owner, async transaction => {
    if (!(await transaction.get(reference)).exists()) transaction.set(reference, valid);
  });
  await assertSucceeds(createOnce());
  await assertSucceeds(createOnce());
  await assertSucceeds(setDoc(doc(owner, path, 'c25d1447-e37c-4cec-ab3a-014172c41dab'), {
    ...valid, dia: today, categoria: 'FOOD', centimos: 20000,
  }));
  assert.equal((await assertSucceeds(getDocs(collection(owner, path)))).size, 2);
  const revision = 'b7874e62-900c-4422-bd3e-a8a5f53f2d0c';
  for (const outsider of [env.authenticatedContext('bob'), env.unauthenticatedContext()]) {
    const otherReference = doc(outsider.firestore(), path, id);
    await assertFails(updateDoc(otherReference, { centimos: 1, revision }));
    await assertFails(deleteDoc(otherReference));
  }
  for (const changes of [{ centimos: -1 }, { centimos: 0.01 }, { categoria: 'INVALID' },
    { dia: valid.dia - 1 }, { dia: today + 2 }, { nota: 'x'.repeat(501) }, { extra: true }]) {
    await assertFails(updateDoc(reference, { ...changes, revision }));
  }
  const corrected = { centimos: 40000, dia: today, categoria: 'HOUSING', nota: 'Corrección', revision };
  await assertFails(updateDoc(reference, { centimos: 40000 }));
  await assertSucceeds(updateDoc(reference, corrected));
  await assertFails(updateDoc(reference, { centimos: 50000, revision }));
  assert.deepEqual((await assertSucceeds(getDoc(reference))).data(), corrected);
  assert.equal((await assertSucceeds(getDocs(collection(owner, path)))).size, 2);
  const marker = doc(owner, 'usuarios/alice/movimientosEliminados', id);
  await assertFails(setDoc(marker, { eliminado: true }));
  await assertFails(deleteDoc(reference));
  const deleteOnce = () => runTransaction(owner, async transaction => {
    if ((await transaction.get(reference)).exists()) {
      transaction.set(marker, { eliminado: true });
      transaction.delete(reference);
    }
  });
  await assertSucceeds(deleteOnce());
  await assertSucceeds(deleteOnce());
  assert.equal((await assertSucceeds(getDoc(reference))).exists(), false);
  await assertFails(createOnce());
  await assertFails(deleteDoc(marker));
  assert.equal((await assertSucceeds(getDocs(collection(owner, path)))).size, 1);
});
