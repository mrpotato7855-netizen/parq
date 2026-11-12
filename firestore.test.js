const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
});

test("Authenticated user: can create own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_w",
      displayName: "Alice Wonderland",
      bio: "Hello world",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob_builder",
      displayName: "Bob",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Chats: participant can read chat", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc("chat_1").set({
      chatId: "chat_1",
      type: "direct",
      ownerId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      isPublic: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("chats").doc("chat_1").get());

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(charlieDb.collection("chats").doc("chat_1").get());
});

test("Messages: sender can create message in chat", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      messageId: "msg_1",
      chatId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      type: "text",
      content: "Hello Bob!",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
