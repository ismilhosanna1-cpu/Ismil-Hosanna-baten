const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write conversations or reminders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/conversations`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_1").set({
      title: "Unauthorized",
      languageCode: "en",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can create, update, list, and delete conversations and messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_1").set({
      title: "Morning Briefing",
      languageCode: "bn",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_1").update({
      title: "Updated Briefing",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).get()
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations/conv_1/messages`).doc("msg_1").set({
      role: "user",
      content: "হ্যালো ইয়েসমিন",
      verifiedDeviceData: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations/conv_1/messages`).doc("msg_2").set({
      role: "model",
      content: "হ্যালো! কেমন সাহায্য করতে পারি?",
      modelUsed: "gemini-3.8-flash",
      verifiedDeviceData: false,
      actionSummary: "Gemini reply",
      createdAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations/conv_1/messages`).get()
  );

  // Messages are immutable once created
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/conversations/conv_1/messages`).doc("msg_1").update({
      content: "Tampered",
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_1").delete()
  );
});

test("Shadow updates, future timestamps, and extra @DocumentId fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Extra "id" field must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_bad_id").set({
      id: "rem_bad_id",
      title: "Drink Water",
      notes: "",
      triggerAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_future").set({
      title: "Drink Water",
      notes: "",
      triggerAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Valid reminder create & completion toggle
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").set({
      title: "Drink Water",
      notes: "250ml glass",
      triggerAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow field injection must fail
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      completed: false,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, write, or delete Alice's data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("alice_rem").set({
      title: "Alice Private Reminder",
      notes: "",
      triggerAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).doc("alice_rem").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/reminders`).doc("alice_rem").update({
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).doc("alice_rem").delete());
});
