// Ejecutar una sola vez para marcar al usuario admin:
//   node set-admin.js andreabelensacchi@gmail.com

const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth }              = require("firebase-admin/auth");
const serviceAccount           = require("./service-account.json");

initializeApp({ credential: cert(serviceAccount) });

const email = process.argv[2];
if (!email) { console.error("Uso: node set-admin.js <email>"); process.exit(1); }

getAuth().getUserByEmail(email)
  .then(user => getAuth().setCustomUserClaims(user.uid, { admin: true }))
  .then(() => { console.log(`✓ Custom claim admin:true asignado a ${email}`); process.exit(0); })
  .catch(err => { console.error(err.message); process.exit(1); });
