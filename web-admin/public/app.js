import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.0/firebase-app.js";
import {
  getAuth, signInWithEmailAndPassword, sendPasswordResetEmail,
  GoogleAuthProvider, signInWithPopup,
  signOut, onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.14.0/firebase-auth.js";
import {
  getFirestore, collection, getDocs, addDoc, updateDoc, deleteDoc,
  doc, serverTimestamp, orderBy, query, writeBatch
} from "https://www.gstatic.com/firebasejs/10.14.0/firebase-firestore.js";
import {
  getStorage, ref, uploadBytesResumable, getDownloadURL
} from "https://www.gstatic.com/firebasejs/10.14.0/firebase-storage.js";

// ── Firebase config ───────────────────────────────────────────────────────────
const firebaseConfig = {
  apiKey:            "AIzaSyDH1cUtSCadKdFm60U79UZGUOzZDkmIx2s",
  authDomain:        "estrella-de-belen-85a2b.firebaseapp.com",
  projectId:         "estrella-de-belen-85a2b",
  storageBucket:     "estrella-de-belen-85a2b.firebasestorage.app",
  messagingSenderId: "1033321694994",
  appId:             "REPLACE_WITH_WEB_APP_ID"
};

const app     = initializeApp(firebaseConfig);
const auth    = getAuth(app);
const db      = getFirestore(app);
const storage = getStorage(app);

// ── State ─────────────────────────────────────────────────────────────────────
let editingId        = null;
let uploadedImageUrl = null;
let uploadedAudioUrl = null;
let pendingDeleteId  = null;
let draggedEl        = null;
let dragFromHandle   = false;

// ── Auth ──────────────────────────────────────────────────────────────────────
onAuthStateChanged(auth, async user => {
  if (user) {
    const token = await user.getIdTokenResult();
    if (token.claims.admin !== true) {
      await signOut(auth);
      const errEl = document.getElementById("login-error");
      errEl.textContent = "No tenés acceso al panel de administración.";
      hide("login-forgot-state");
      show("login-form-state");
      show(errEl);
      return;
    }
    show("admin-view"); hide("login-view");
    document.getElementById("admin-email").textContent = user.email;
    loadMeditations();
  } else {
    show("login-view"); hide("admin-view");
  }
});

document.getElementById("login-btn").addEventListener("click", signIn);
document.getElementById("login-password").addEventListener("keydown", e => {
  if (e.key === "Enter") signIn();
});
document.getElementById("login-email").addEventListener("keydown", e => {
  if (e.key === "Enter") document.getElementById("login-password").focus();
});

async function signIn() {
  const email    = document.getElementById("login-email").value.trim();
  const password = document.getElementById("login-password").value;
  const errEl    = document.getElementById("login-error");
  hide(errEl);

  try {
    await signInWithEmailAndPassword(auth, email, password);
  } catch (e) {
    errEl.textContent = friendlyAuthError(e.code);
    show(errEl);
  }
}

document.getElementById("logout-btn").addEventListener("click", () => signOut(auth));

document.getElementById("google-btn").addEventListener("click", async () => {
  const errEl  = document.getElementById("login-error");
  const btn    = document.getElementById("google-btn");
  const btnTxt = document.getElementById("google-btn-text");
  hide(errEl);
  btn.disabled = true;
  btnTxt.textContent = "Conectando...";
  try {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(auth, provider);
  } catch (e) {
    if (e.code !== "auth/popup-closed-by-user") {
      errEl.textContent = friendlyAuthError(e.code);
      show(errEl);
    }
  } finally {
    btn.disabled = false;
    btnTxt.textContent = "Continuar con Google";
  }
});

// ── Forgot password ───────────────────────────────────────────────────────────
document.getElementById("forgot-link").addEventListener("click", () => {
  document.getElementById("reset-email").value = document.getElementById("login-email").value;
  hide("reset-success");
  hide("reset-error");
  hide("login-form-state");
  show("login-forgot-state");
});

document.getElementById("back-to-login").addEventListener("click", () => {
  hide("login-forgot-state");
  show("login-form-state");
});

document.getElementById("reset-btn").addEventListener("click", async () => {
  const email   = document.getElementById("reset-email").value.trim();
  const errEl   = document.getElementById("reset-error");
  const successEl = document.getElementById("reset-success");
  hide(errEl); hide(successEl);

  if (!email) {
    errEl.textContent = "Ingresá tu correo electrónico.";
    show(errEl);
    return;
  }

  const btn = document.getElementById("reset-btn");
  btn.disabled = true;
  try {
    await sendPasswordResetEmail(auth, email);
    show(successEl);
    btn.textContent = "Reenviar enlace";
  } catch (e) {
    errEl.textContent = friendlyAuthError(e.code);
    show(errEl);
  } finally {
    btn.disabled = false;
  }
});

// ── Load meditations ──────────────────────────────────────────────────────────
async function loadMeditations() {
  const listEl = document.getElementById("meditation-list");
  listEl.innerHTML = '<div class="loading">Cargando...</div>';
  try {
    const q = query(collection(db, "meditations"), orderBy("order", "asc"));
    const snap = await getDocs(q);
    if (snap.empty) {
      listEl.innerHTML = '<div class="loading">Todavía no hay meditaciones.</div>';
      return;
    }
    listEl.innerHTML = "";
    snap.forEach(docSnap => listEl.appendChild(buildCard(docSnap.id, docSnap.data())));
  } catch (e) {
    listEl.innerHTML = `<div class="loading" style="color:var(--error)">Error al cargar: ${e.message}</div>`;
  }
}

function buildCard(id, data) {
  const card = document.createElement("div");
  card.className = "med-card";
  card.dataset.id = id;
  card.setAttribute("draggable", "true");

  const halo = data.haloColor || "#9890B8";
  const mins = Math.round((data.durationSeconds || 0) / 60);
  const isFree = data.isFree === true;
  const freeBadge = isFree
    ? `<span style="background:#e8f5e9;color:#388e3c;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600">Libre</span>`
    : "";

  card.innerHTML = `
    ${!isFree ? `<div class="premium-ribbon">✦</div>` : ""}
    <div class="drag-handle" title="Arrastrar para reordenar">⠿ ⠿</div>
    <div class="med-card-image">
      ${data.imageUrl
        ? `<img src="${data.imageUrl}" alt="${data.title}" />`
        : `<div class="med-card-halo" style="background:${halo}"></div>🎵`
      }
    </div>
    <div class="med-card-body">
      <div class="med-card-title">${data.title || "Sin título"}</div>
      <div class="med-card-meta">
        ${data.category ? `<span class="pill">${data.category}</span>` : ""}
        <span>${mins} min</span>
        <span style="display:inline-block;width:14px;height:14px;border-radius:50%;background:${halo};vertical-align:middle"></span>
        ${freeBadge}
      </div>
    </div>
    <div class="med-card-actions">
      <button class="btn-ghost btn-sm edit-btn">Editar</button>
      <button class="btn-ghost btn-sm delete-btn">Eliminar</button>
    </div>
  `;

  const handle = card.querySelector(".drag-handle");
  handle.addEventListener("mousedown", () => { dragFromHandle = true; });
  handle.addEventListener("mouseup",   () => { dragFromHandle = false; });

  card.addEventListener("dragstart",  onDragStart);
  card.addEventListener("dragover",   onDragOver);
  card.addEventListener("dragleave",  onDragLeave);
  card.addEventListener("drop",       onDrop);
  card.addEventListener("dragend",    onDragEnd);

  card.querySelector(".edit-btn").addEventListener("click",   () => openModal(id, data));
  card.querySelector(".delete-btn").addEventListener("click", () => confirmDelete(id, data.title));
  return card;
}

// ── Drag & drop reorder ───────────────────────────────────────────────────────
function onDragStart(e) {
  if (!dragFromHandle) { e.preventDefault(); return; }
  draggedEl = e.currentTarget;
  e.dataTransfer.effectAllowed = "move";
  setTimeout(() => draggedEl?.classList.add("dragging"), 0);
}

function onDragOver(e) {
  e.preventDefault();
  if (!draggedEl) return;
  const target = e.currentTarget;
  if (target === draggedEl) return;
  document.querySelectorAll(".med-card.drag-over").forEach(el => el.classList.remove("drag-over"));
  target.classList.add("drag-over");
}

function onDragLeave(e) {
  if (!e.currentTarget.contains(e.relatedTarget)) {
    e.currentTarget.classList.remove("drag-over");
  }
}

function onDrop(e) {
  e.preventDefault();
  const target = e.currentTarget;
  target.classList.remove("drag-over");
  if (!draggedEl || target === draggedEl) return;

  const list    = document.getElementById("meditation-list");
  const cards   = [...list.querySelectorAll(".med-card")];
  const fromIdx = cards.indexOf(draggedEl);
  const toIdx   = cards.indexOf(target);

  if (fromIdx < toIdx) {
    target.after(draggedEl);
  } else {
    target.before(draggedEl);
  }

  saveNewOrder();
}

function onDragEnd() {
  draggedEl?.classList.remove("dragging");
  document.querySelectorAll(".drag-over").forEach(el => el.classList.remove("drag-over"));
  draggedEl      = null;
  dragFromHandle = false;
}

async function saveNewOrder() {
  const cards = [...document.querySelectorAll("#meditation-list .med-card")];
  const batch = writeBatch(db);
  cards.forEach((card, idx) => {
    batch.update(doc(db, "meditations", card.dataset.id), { order: idx + 1 });
  });
  try {
    await batch.commit();
  } catch (e) {
    console.error("Error al guardar el orden:", e);
  }
}

// ── Modal ─────────────────────────────────────────────────────────────────────
document.getElementById("new-btn").addEventListener("click",    () => openModal(null, null));
document.getElementById("modal-close").addEventListener("click", closeModal);
document.getElementById("cancel-btn").addEventListener("click",  closeModal);

function openModal(id, data) {
  editingId        = id;
  uploadedImageUrl = data?.imageUrl || null;
  uploadedAudioUrl = data?.audioUrl || null;

  document.getElementById("modal-title").textContent = id ? "Editar meditación" : "Nueva meditación";
  document.getElementById("save-text").textContent   = id ? "Guardar cambios"   : "Publicar meditación";

  document.getElementById("f-title").value       = data?.title       || "";
  document.getElementById("f-description").value = data?.description || "";
  document.getElementById("f-category").value    = data?.category    || "";
  document.getElementById("f-duration").value    = data?.durationSeconds || "";

  const halo = data?.haloColor || "#9890B8";
  document.getElementById("f-halo-color").value = halo;
  document.getElementById("halo-hex").textContent = halo;
  updateHaloPreview(halo);
  document.querySelectorAll(".swatch").forEach(s =>
    s.classList.toggle("active", s.dataset.color === halo)
  );

  resetDropZone("image", data?.imageUrl || null);
  resetDropZone("audio", data?.audioUrl || null);

  document.getElementById("f-is-free").checked = data?.isFree === true;

  hide("form-error");
  show("modal-overlay");
}

function closeModal() {
  hide("modal-overlay");
  editingId        = null;
  uploadedImageUrl = null;
  uploadedAudioUrl = null;
}

function resetDropZone(type, existingUrl) {
  const progress = document.getElementById(`${type}-progress`);
  const urlDisp  = document.getElementById(`${type}-url-display`);
  const input    = document.getElementById(`f-${type}`);
  input.value = "";
  hide(progress);
  if (existingUrl) {
    urlDisp.textContent = "✓ Archivo actual: " + truncate(existingUrl, 60);
    show(urlDisp);
  } else {
    hide(urlDisp);
  }
  if (type === "image") {
    const preview = document.getElementById("image-preview");
    if (existingUrl) { preview.src = existingUrl; show(preview); }
    else hide(preview);
  } else {
    const name = document.getElementById("audio-filename");
    if (existingUrl) { name.textContent = "✓ Audio cargado"; show(name); }
    else hide(name);
  }
}

// ── Save ──────────────────────────────────────────────────────────────────────
document.getElementById("save-btn").addEventListener("click", async () => {
  const title       = document.getElementById("f-title").value.trim();
  const description = document.getElementById("f-description").value.trim();
  const category    = document.getElementById("f-category").value.trim();
  const durationRaw = document.getElementById("f-duration").value;
  const haloColor   = document.getElementById("f-halo-color").value;
  const imageFile   = document.getElementById("f-image").files[0];
  const audioFile   = document.getElementById("f-audio").files[0];

  const errEl = document.getElementById("form-error");
  hide(errEl);

  if (!title) { show(errEl); errEl.textContent = "El título es obligatorio."; return; }
  if (!editingId && !audioFile && !uploadedAudioUrl) {
    show(errEl); errEl.textContent = "El archivo de audio es obligatorio."; return;
  }

  setSaving(true);

  try {
    if (imageFile) {
      uploadedImageUrl = await uploadFile(imageFile, `meditations/images/${Date.now()}_${imageFile.name}`, "image");
    }
    if (audioFile) {
      uploadedAudioUrl = await uploadFile(audioFile, `meditations/audio/${Date.now()}_${audioFile.name}`, "audio");
    }

    const payload = {
      title,
      description,
      category,
      durationSeconds: durationRaw ? parseInt(durationRaw) : 0,
      haloColor,
      imageUrl: uploadedImageUrl || "",
      audioUrl: uploadedAudioUrl || "",
      isFree: document.getElementById("f-is-free").checked,
    };

    if (editingId) {
      await updateDoc(doc(db, "meditations", editingId), payload);
    } else {
      const cardCount = document.querySelectorAll("#meditation-list .med-card").length;
      payload.order     = cardCount + 1;
      payload.createdAt = serverTimestamp();
      await addDoc(collection(db, "meditations"), payload);
    }

    closeModal();
    loadMeditations();
  } catch (e) {
    show(errEl);
    errEl.textContent = "Error al guardar: " + e.message;
  } finally {
    setSaving(false);
  }
});

function setSaving(on) {
  const btn  = document.getElementById("save-btn");
  const text = document.getElementById("save-text");
  const spin = document.getElementById("save-spinner");
  btn.disabled = on;
  on ? (hide(text), show(spin)) : (show(text), hide(spin));
}

// ── File upload ───────────────────────────────────────────────────────────────
function uploadFile(file, path, type) {
  return new Promise((resolve, reject) => {
    const storageRef = ref(storage, path);
    const task       = uploadBytesResumable(storageRef, file);
    const progress   = document.getElementById(`${type}-progress`);
    const bar        = progress.querySelector(".progress-bar");
    const label      = progress.querySelector(".progress-text");
    show(progress);

    task.on("state_changed",
      snap => {
        const pct = Math.round(snap.bytesTransferred / snap.totalBytes * 100);
        bar.style.width   = pct + "%";
        label.textContent = pct + "%";
      },
      reject,
      async () => {
        const url = await getDownloadURL(task.snapshot.ref);
        const urlDisp = document.getElementById(`${type}-url-display`);
        urlDisp.textContent = "✓ Subido correctamente";
        show(urlDisp);
        if (type === "audio") {
          const dur = await getAudioDuration(file);
          if (dur) document.getElementById("f-duration").value = Math.round(dur);
          document.getElementById("audio-filename").textContent = "✓ " + file.name;
          show(document.getElementById("audio-filename"));
        }
        if (type === "image") {
          const preview = document.getElementById("image-preview");
          preview.src = URL.createObjectURL(file);
          show(preview);
        }
        resolve(url);
      }
    );
  });
}

function getAudioDuration(file) {
  return new Promise(resolve => {
    const audio = document.createElement("audio");
    audio.preload = "metadata";
    audio.onloadedmetadata = () => resolve(audio.duration);
    audio.onerror = () => resolve(null);
    audio.src = URL.createObjectURL(file);
  });
}

// ── Delete ────────────────────────────────────────────────────────────────────
function confirmDelete(id, title) {
  pendingDeleteId = id;
  document.getElementById("delete-name").textContent = title || "esta meditación";
  show("delete-overlay");
}

document.getElementById("delete-cancel").addEventListener("click", () => {
  hide("delete-overlay"); pendingDeleteId = null;
});

document.getElementById("delete-confirm").addEventListener("click", async () => {
  if (!pendingDeleteId) return;
  try {
    await deleteDoc(doc(db, "meditations", pendingDeleteId));
    hide("delete-overlay");
    pendingDeleteId = null;
    loadMeditations();
  } catch (e) {
    alert("Error al eliminar: " + e.message);
  }
});

// ── Color picker ──────────────────────────────────────────────────────────────
document.querySelectorAll(".swatch").forEach(swatch => {
  swatch.addEventListener("click", () => {
    const color = swatch.dataset.color;
    document.getElementById("f-halo-color").value = color;
    document.getElementById("halo-hex").textContent = color;
    updateHaloPreview(color);
    document.querySelectorAll(".swatch").forEach(s => s.classList.remove("active"));
    swatch.classList.add("active");
  });
});

document.getElementById("f-halo-color").addEventListener("input", e => {
  document.getElementById("halo-hex").textContent = e.target.value;
  updateHaloPreview(e.target.value);
  document.querySelectorAll(".swatch").forEach(s => s.classList.remove("active"));
});

function updateHaloPreview(color) {
  document.getElementById("halo-preview").style.setProperty("--preview-halo", color);
}

// ── File drop zones ───────────────────────────────────────────────────────────
["image-drop", "audio-drop"].forEach(id => {
  const zone = document.getElementById(id);
  zone.addEventListener("dragover", e => { e.preventDefault(); zone.classList.add("drag-over"); });
  zone.addEventListener("dragleave", () => zone.classList.remove("drag-over"));
  zone.addEventListener("drop", e => {
    e.preventDefault(); zone.classList.remove("drag-over");
    const type  = zone.dataset.type;
    const input = document.getElementById(`f-${type}`);
    const files = e.dataTransfer.files;
    if (files.length) {
      const dt = new DataTransfer();
      dt.items.add(files[0]);
      input.files = dt.files;
      input.dispatchEvent(new Event("change"));
    }
  });
});

document.getElementById("f-audio").addEventListener("change", async e => {
  const file = e.target.files[0];
  if (!file) return;
  document.getElementById("audio-filename").textContent = file.name;
  show(document.getElementById("audio-filename"));
  const dur = await getAudioDuration(file);
  if (dur) document.getElementById("f-duration").value = Math.round(dur);
});

document.getElementById("f-image").addEventListener("change", e => {
  const file = e.target.files[0];
  if (!file) return;
  const preview = document.getElementById("image-preview");
  preview.src = URL.createObjectURL(file);
  show(preview);
});

// ── Helpers ───────────────────────────────────────────────────────────────────
function show(el) {
  const e = typeof el === "string" ? document.getElementById(el) : el;
  e?.classList.remove("hidden");
}
function hide(el) {
  const e = typeof el === "string" ? document.getElementById(el) : el;
  e?.classList.add("hidden");
}
function truncate(str, n) {
  return str.length > n ? str.slice(0, n) + "…" : str;
}
function friendlyAuthError(code) {
  const map = {
    "auth/invalid-email":            "El correo no es válido.",
    "auth/user-not-found":           "No existe una cuenta con ese correo.",
    "auth/wrong-password":           "Contraseña incorrecta.",
    "auth/invalid-credential":       "Email o contraseña incorrectos.",
    "auth/too-many-requests":        "Demasiados intentos. Intentá más tarde.",
    "auth/user-disabled":            "Esta cuenta fue deshabilitada.",
    "auth/popup-blocked-by-browser": "El navegador bloqueó la ventana emergente. Permitila e intentá de nuevo.",
    "auth/unauthorized-domain":      "Este dominio no está autorizado para Google Sign-In.",
    "auth/operation-not-allowed":    "Google Sign-In no está habilitado en este proyecto.",
    "auth/network-request-failed":   "Error de red. Verificá tu conexión e intentá de nuevo.",
  };
  return map[code] || `Error (${code}).`;
}
